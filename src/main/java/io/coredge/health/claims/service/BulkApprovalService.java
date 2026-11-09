package io.coredge.health.claims.service;

import io.coredge.health.claims.audit.ClaimAudit;
import io.coredge.health.claims.audit.ClaimAuditRepository;
import io.coredge.health.claims.db.entity.BatchApprovalRequest;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.BatchApprovalRequestRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.dto.BulkApprovalRequest;
import io.coredge.health.claims.dto.BulkApprovalResponse;
import io.coredge.health.claims.dto.BulkApprovalResponse.ApprovedClaimInfo;
import io.coredge.health.claims.dto.BulkApprovalResponse.FailedClaimInfo;
import io.coredge.health.claims.enums.BatchApprovalStatus;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.service.validation.ValidationStrategyRegistry;
import io.coredge.health.claims.worker.SendClaimForReProcessPaymentHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.coredge.health.claims.context.HealthIdContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.io.IOException;
import io.coredge.health.claims.models.claims.ClaimDataResponse;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for handling bulk approval of claims.
 * Supports processing large volumes (~100,000 claims) with:
 * - Batch processing to manage memory
 * - Database-level locking (FOR UPDATE SKIP LOCKED) for race condition prevention
 * - Transactional integrity per batch
 * - ERP integration for approved claims
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkApprovalService {

    private static final String AUDIT_STATUS_REPROCESS = "reprocess";
    private static final String AUDIT_STATUS_REJECTED = "REJECTED";

    @Value("${bulk.approval.batch.size:1000}")
    private int defaultBatchSize;

    @Value("${bulk.approval.erp.batch.size:100}")
    private int erpBatchSize;

    private final InsuranceClaimsRepository claimsRepository;
    private final ClaimAuditRepository claimAuditRepository;
    private final LineItemRepository lineItemRepository;
    private final SendClaimForReProcessPaymentHandler erpPaymentHandler;
    private final ValidationStrategyRegistry validationRegistry;
    private final BatchApprovalRequestRepository batchApprovalRequestRepository;

    /**
     * Resolves the approver name from session if not provided.
     * Falls back to HealthIdContextHolder.requestSource() which contains the logged-in user.
     */
    private String resolveApproverName(String providedApproverName) {
        if (providedApproverName != null && !providedApproverName.isBlank()) {
            return providedApproverName;
        }
        String sessionUser = HealthIdContextHolder.userId();
        log.debug("Resolving approver name from session: '{}'", sessionUser);
        return sessionUser.isBlank() ? "SYSTEM" : sessionUser;
    }

    /**
     * Process bulk approval of claims based on the request criteria.
     *
     * @param request The bulk approval request containing filter criteria
     * @return Response with counts and details of approved/failed claims
     */
    public BulkApprovalResponse processBulkApproval(BulkApprovalRequest request) {
        String processingId = UUID.randomUUID().toString();
        String approverName = resolveApproverName(null);
        log.info("Starting bulk approval process [processingId={}] for shaCode={}, dateRange={} to {}, initiatedBy={}",
                processingId, request.getShaCode(), request.getStartDate(), request.getEndDate(), approverName);

        // If approveAll is false, just return the count (preview mode)
        if (!request.isApproveAll()) {
            return getApprovalPreview(request, processingId);
        }

        // 1. Create batch record with PENDING status
        BatchApprovalRequest batchRecord = BatchApprovalRequest.builder()
                .batchStatus(BatchApprovalStatus.PENDING)
                .initiatedBy(approverName)
                .interventionCode(request.getShaCode())
                .periodStart(request.getStartDate())
                .periodEnd(request.getEndDate())
                .statusFilter(request.getStatus())
                .build();
        batchRecord = batchApprovalRequestRepository.save(batchRecord);
        Long batchId = batchRecord.getId();
        log.info("Created batch record [batchId={}]", batchId);

        // 2. Update to PROCESSING
        batchRecord.setBatchStatus(BatchApprovalStatus.PROCESSING);
        batchApprovalRequestRepository.save(batchRecord);

        List<ApprovedClaimInfo> allApproved = new ArrayList<>();
        List<FailedClaimInfo> allFailed = new ArrayList<>();
        int totalProcessed = 0;
        Set<UUID> processedClaimIds = new java.util.HashSet<>();

        // 3. Process in batches until no more NEW claims are found
        int maxIterations = 10000;
        int iteration = 0;
        
        try {
            while (iteration < maxIterations) {
                List<InsuranceClaim> batch = lockAndFetchBatch(request, defaultBatchSize);
                if (batch.isEmpty()) {
                    break;
                }

                List<InsuranceClaim> newClaims = batch.stream()
                        .filter(c -> !processedClaimIds.contains(c.getClaimId()))
                        .toList();

                if (newClaims.isEmpty()) {
                    log.info("No new claims to process in batch, stopping [batchId={}]", batchId);
                    break;
                }

                log.info("Processing batch of {} claims ({} new) [batchId={}]", 
                        batch.size(), newClaims.size(), batchId);
                BatchResult result = processBatch(newClaims, approverName, String.valueOf(batchId));
                
                newClaims.forEach(c -> processedClaimIds.add(c.getClaimId()));
                
                allApproved.addAll(result.approved);
                allFailed.addAll(result.failed);
                totalProcessed += newClaims.size();

                log.info("Batch complete: {} approved, {} failed [batchId={}]",
                        result.approved.size(), result.failed.size(), batchId);

                iteration++;
            }

            if (iteration >= maxIterations) {
                log.warn("Bulk approval hit max iteration limit [batchId={}]", batchId);
            }
        } catch (Exception e) {
            log.error("Bulk approval processing error [batchId={}]: {}", batchId, e.getMessage(), e);
        }

        // 4. Finalize batch record
        batchRecord.setTotalClaims(totalProcessed);
        batchRecord.setSuccessful(allApproved.size());
        batchRecord.setFailed(allFailed.size());
        batchRecord.setBatchStatus(computeBatchStatus(allApproved.size(), allFailed.size()));
        batchApprovalRequestRepository.save(batchRecord);

        log.info("Bulk approval complete [batchId={}, processingId={}]: {} total, {} approved, {} failed, status={}",
                batchId, processingId, totalProcessed, allApproved.size(), allFailed.size(), batchRecord.getBatchStatus());

        return BulkApprovalResponse.builder()
                .processingId(processingId)
                .batchId(batchId)
                .totalEligible(totalProcessed)
                .totalApproved(allApproved.size())
                .totalFailed(allFailed.size())
                .approvedClaims(allApproved)
                .failedClaims(allFailed)
                .build();
    }

    /**
     * Computes the final batch status based on approved/failed counts.
     */
    private BatchApprovalStatus computeBatchStatus(int approved, int failed) {
        if (approved == 0 && failed == 0) return BatchApprovalStatus.COMPLETED;
        if (failed == 0) return BatchApprovalStatus.COMPLETED;
        if (approved == 0) return BatchApprovalStatus.FAILED;
        return BatchApprovalStatus.PARTIALLY_COMPLETED;
    }

    /**
     * Preview mode: returns count of eligible claims without approving.
     */
    private BulkApprovalResponse getApprovalPreview(BulkApprovalRequest request, String processingId) {
        int count = claimsRepository.countEligibleClaimsForBulkApproval(
                request.getShaCode(),
                request.getStatus(),
                request.getStartDate(),
                request.getEndDate());

        log.info("Preview mode: {} eligible claims found [processingId={}]", count, processingId);

        return BulkApprovalResponse.builder()
                .processingId(processingId)
                .totalEligible(count)
                .totalApproved(0)
                .totalFailed(0)
                .build();
    }

    /**
     * Lists eligible claims for bulk approval with pagination.
     * Uses the same criteria as the bulk approval processing query to ensure
     * the UI preview matches what will actually be processed.
     */
    public Page<InsuranceClaim> listEligibleClaims(String shaCode, String status,
            LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Page<UUID> claimIds = claimsRepository.listEligibleClaimIdsForBulkApproval(
                shaCode, status, startDate, endDate, pageable);

        if (claimIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<InsuranceClaim> claims = claimsRepository.findByClaimIdIn(claimIds.getContent())
                .orElse(List.of());

        return new PageImpl<>(claims, pageable, claimIds.getTotalElements());
    }

    /**
     * Lists eligible claims for bulk approval, converted to ClaimDataResponse for the UI.
     * Returns a paginated CollectionModel matching the format used by the page-claim endpoint.
     */
    public CollectionModel<EntityModel<ClaimDataResponse>> listEligibleClaimsForUI(
            String shaCode, String status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Page<InsuranceClaim> claims = listEligibleClaims(shaCode, status, startDate, endDate, pageable);

        Page<ClaimDataResponse> responses = claims.map(claim -> {
            ClaimDataResponse response = new ClaimDataResponse();
            org.springframework.beans.BeanUtils.copyProperties(claim, response);
            if (claim.getPatient() != null) {
                response.setPatientName(claim.getPatient().getName());
                response.setCrId(claim.getPatient().getCrId());
                response.setNationalId(claim.getPatient().getNationalId());
            }
            if (claim.getProvider() != null) {
                response.setProviderName(claim.getProvider().getName());
                response.setProviderLevel(claim.getProvider().getLevel());
            }
            response.setCreationDate(claim.getCreationDate() != null
                    ? LocalDate.from(claim.getCreationDate()) : claim.getClaimCreatedAt());
            // Extract shaCode from claim line items
            lineItemRepository.findByClaimId(claim.getClaimId())
                    .flatMap(items -> items.stream().findFirst())
                    .ifPresent(item -> response.setShaCode(item.getCode()));
            response.setStatus(claim.getStatus());
            return response;
        });

        org.springframework.data.web.PagedResourcesAssembler<ClaimDataResponse> assembler =
                new org.springframework.data.web.PagedResourcesAssembler<>(null, null);
        return assembler.toModel(responses);
    }

    /**
     * Fetches and locks a batch of claims for processing.
     * Uses REQUIRES_NEW propagation to ensure each batch is its own transaction.
     * FOR UPDATE SKIP LOCKED prevents race conditions across multiple servers.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<InsuranceClaim> lockAndFetchBatch(BulkApprovalRequest request, int batchSize) {
        // First get the IDs with FOR UPDATE SKIP LOCKED to acquire locks
        List<UUID> claimIds = claimsRepository.findEligibleClaimIdsForBulkApproval(
                request.getShaCode(),
                request.getStatus(),
                request.getStartDate(),
                request.getEndDate(),
                batchSize);
        
        if (claimIds.isEmpty()) {
            return List.of();
        }
        
        // Then fetch the full claim entities
        return claimsRepository.findByClaimIdIn(claimIds).orElse(List.of());
    }

    /**
     * Process a batch of claims: validate, create audit records, add process notes.
     * The processingId is the batch ID (as String) from batch_approval_request.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult processBatch(List<InsuranceClaim> claims, String approverName, String batchId) {
        List<ApprovedClaimInfo> approved = new ArrayList<>();
        List<FailedClaimInfo> failed = new ArrayList<>();
        List<UUID> claimIdsToUpdate = new ArrayList<>();
        List<ClaimAudit> auditRecords = new ArrayList<>();

        for (InsuranceClaim claim : claims) {
            try {
                // Validate: claim must have exactly one claim item
                List<ClaimLineItem> claimItems = lineItemRepository.findByClaimId(claim.getClaimId())
                        .orElse(List.of());
                if (claimItems.size() != 1) {
                    failed.add(FailedClaimInfo.builder()
                            .claimId(claim.getClaimId().toString())
                            .interventionCode(claimItems.isEmpty() ? "N/A" : claimItems.get(0).getCode())
                            .reason("Claim has " + claimItems.size() + " items, expected exactly 1")
                            .build());
                    continue;
                }

                ClaimLineItem claimItem = claimItems.get(0);
                String previousStatus = claim.getStatus().name();

                // Package-specific validation (SHA-16, etc.)
                if (validationRegistry.hasValidation(claimItem.getCode())) {
                    var rejectionReason = validationRegistry.validate(claimItem.getCode(), claim);
                    if (rejectionReason.isPresent()) {
                        // Insert REJECTED status into claim_reprocess_audit
                        ClaimAudit rejectionAudit = claimAuditRepository
                                .findByClaimIdAndInterventionCode(claim.getClaimId(), claimItem.getCode())
                                .orElse(ClaimAudit.builder()
                                        .claimId(claim.getClaimId())
                                        .interventionCode(claimItem.getCode())
                                        .build());
                        rejectionAudit.setSequence(claimItem.getSequence());
                        rejectionAudit.setAuditStatus(AUDIT_STATUS_REJECTED);
                        rejectionAudit.setRemarks(rejectionReason.get());
                        rejectionAudit.setApproverName(approverName);
                        rejectionAudit.setBulkProcessingId(batchId);
                        rejectionAudit.setProcessed(false);
                        claimAuditRepository.save(rejectionAudit);
                        
                        log.info("Surveillance rejection for claim {}: {}", claim.getClaimId(), rejectionReason.get());
                        
                        failed.add(FailedClaimInfo.builder()
                                .claimId(claim.getClaimId().toString())
                                .interventionCode(claimItem.getCode())
                                .reason(rejectionReason.get())
                                .build());
                        continue;
                    }
                }

                // Add to batch update list
                claimIdsToUpdate.add(claim.getClaimId());

                // Create or update audit record (upsert for composite unique constraint on claim_id + intervention_code)
                ClaimAudit audit = claimAuditRepository.findByClaimIdAndInterventionCode(claim.getClaimId(), claimItem.getCode())
                        .orElse(ClaimAudit.builder()
                                .claimId(claim.getClaimId())
                                .interventionCode(claimItem.getCode())
                                .build());
                
                // Update audit fields — link to batch via batchId
                audit.setSequence(claimItem.getSequence());
                audit.setAuditStatus(AUDIT_STATUS_REPROCESS);
                audit.setProcessed(false);
                audit.setApproverName(approverName);
                audit.setBulkProcessingId(batchId);
                audit.setRemarks("Bulk approval");
                
                auditRecords.add(audit);

                approved.add(ApprovedClaimInfo.builder()
                        .claimId(claim.getClaimId().toString())
                        .interventionCode(claimItem.getCode())
                        .previousStatus(previousStatus)
                        .build());

            } catch (Exception e) {
                log.warn("Error processing claim {}: {}", claim.getClaimId(), e.getMessage());
                failed.add(FailedClaimInfo.builder()
                        .claimId(claim.getClaimId().toString())
                        .reason("Processing error: " + e.getMessage())
                        .build());
            }
        }

        // Batch update claims
        if (!claimIdsToUpdate.isEmpty()) {
            int updated = claimsRepository.bulkUpdateStatusAndApprover(
                    claimIdsToUpdate,
                    approverName);
            log.debug("Bulk updated {} claims", updated);
        }

        // Save audit records
        if (!auditRecords.isEmpty()) {
            claimAuditRepository.saveAll(auditRecords);
            log.debug("Saved {} audit records", auditRecords.size());
        }

        return new BatchResult(approved, failed);
    }

    /**
     * Triggers ERP payment processing for approved claims.
     * Processes in sub-batches to avoid overwhelming ERP system.
     */
    private void triggerErpForApprovedClaims(List<String> approvedClaimIds) {
        log.info("Triggering ERP for {} approved claims", approvedClaimIds.size());

        // Process in sub-batches
        for (int i = 0; i < approvedClaimIds.size(); i += erpBatchSize) {
            int end = Math.min(i + erpBatchSize, approvedClaimIds.size());
            List<String> batch = approvedClaimIds.subList(i, end);

            try {
                erpPaymentHandler.handleReProcessPaymentErp(batch, true, true);
                log.debug("ERP batch {} to {} processed successfully", i, end);
            } catch (Exception e) {
                // ERP failures don't rollback DB - claims are approved but logged as ERP-pending
                log.error("ERP integration failed for batch {} to {}: {}", i, end, e.getMessage());
            }
        }
    }

    /**
     * Result holder for batch processing.
     */
    private record BatchResult(List<ApprovedClaimInfo> approved, List<FailedClaimInfo> failed) {}

    // ============ EXCEL UPLOAD BULK APPROVAL ============

    private static final List<ExtensionStateCodeEnum> ALLOWED_STATUSES = List.of(
            ExtensionStateCodeEnum.MEDICAL_REVIEW,
            ExtensionStateCodeEnum.MANUAL_REVIEW,
            ExtensionStateCodeEnum.APPROVED
    );

    /**
     * Process bulk approval from uploaded Excel file containing claim IDs.
     * Validates each claim exists and is in allowed status before approving.
     *
     * @param file Excel file with claim IDs in the first column
     * @return Response with counts and details of approved/failed claims
     */
    public BulkApprovalResponse processBulkApprovalFromExcel(MultipartFile file) {
        String processingId = UUID.randomUUID().toString();
        log.info("Starting Excel bulk approval process [processingId={}], file: {}", processingId, file.getOriginalFilename());

        // 1. Parse claim IDs from Excel
        List<String> claimIdStrings;
        try {
            claimIdStrings = parseClaimIdsFromFile(file);
        } catch (IOException e) {
            log.error("Failed to parse Excel file: {}", e.getMessage());
            return BulkApprovalResponse.builder()
                    .processingId(processingId)
                    .totalEligible(0)
                    .totalApproved(0)
                    .totalFailed(1)
                    .failedClaims(List.of(FailedClaimInfo.builder()
                            .claimId("N/A")
                            .reason("Failed to parse Excel file: " + e.getMessage())
                            .build()))
                    .build();
        }

        log.info("Parsed {} claim IDs from Excel [processingId={}]", claimIdStrings.size(), processingId);

        if (claimIdStrings.isEmpty()) {
            return BulkApprovalResponse.builder()
                    .processingId(processingId)
                    .totalEligible(0)
                    .totalApproved(0)
                    .totalFailed(0)
                    .build();
        }

        // 2. Convert to UUIDs and track invalid ones
        List<UUID> claimIds = new ArrayList<>();
        List<FailedClaimInfo> failed = new ArrayList<>();

        for (String idStr : claimIdStrings) {
            try {
                claimIds.add(UUID.fromString(idStr.trim()));
            } catch (IllegalArgumentException e) {
                failed.add(FailedClaimInfo.builder()
                        .claimId(idStr)
                        .reason("Invalid UUID format")
                        .build());
            }
        }

        log.info("claimIds: {}", claimIds);
        // 3. Fetch claims with allowed statuses
        List<InsuranceClaim> validClaims = claimsRepository.findByClaimIdInAndStatusIn(claimIds, ALLOWED_STATUSES);
        log.info("validClaims: {}", validClaims);
        Set<UUID> validClaimIds = validClaims.stream()
                .map(InsuranceClaim::getClaimId)
                .collect(Collectors.toSet());

        // 4. Track claims not found or in wrong status
        for (UUID id : claimIds) {
            if (!validClaimIds.contains(id)) {
                failed.add(FailedClaimInfo.builder()
                        .claimId(id.toString())
                        .reason("Claim not found or not in allowed status (MEDICAL_REVIEW, MANUAL_REVIEW, APPROVED)")
                        .build());
            }
        }

        log.info("Found {} valid claims out of {} [processingId={}]", validClaims.size(), claimIds.size(), processingId);

        // 5. Process valid claims in batches
        List<ApprovedClaimInfo> allApproved = new ArrayList<>();
        List<List<InsuranceClaim>> batches = partitionList(validClaims, defaultBatchSize);

        for (List<InsuranceClaim> batch : batches) {
            BatchResult result = processBatch(batch, resolveApproverName(null), processingId);
            allApproved.addAll(result.approved());
            failed.addAll(result.failed());
        }

        log.info("Excel bulk approval complete [processingId={}]: {} approved, {} failed",
                processingId, allApproved.size(), failed.size());

        return BulkApprovalResponse.builder()
                .processingId(processingId)
                .totalEligible(claimIdStrings.size())
                .totalApproved(allApproved.size())
                .totalFailed(failed.size())
                .approvedClaims(allApproved)
                .failedClaims(failed)
                .build();
    }

    /**
     * Parses claim IDs from the first column of an Excel or CSV file.
     * Skips header row and empty cells.
     * Supports .xlsx, .xls, and .csv file formats.
     */
    private List<String> parseClaimIdsFromFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".csv")) {
            return parseClaimIdsFromCsv(file);
        }
        return parseClaimIdsFromExcel(file);
    }

    /**
     * Parses claim IDs from the first column of a CSV file.
     * Skips header row and empty lines.
     */
    private List<String> parseClaimIdsFromCsv(MultipartFile file) throws IOException {
        List<String> claimIds = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isFirstRow = true;
            
            while ((line = reader.readLine()) != null) {
                // Skip header row
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }
                
                if (line.isBlank()) {
                    continue;
                }
                
                // Get first column (handle comma-separated values)
                String[] columns = line.split(",");
                if (columns.length > 0) {
                    String value = columns[0].trim();
                    // Remove quotes if present
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (!value.isBlank()) {
                        claimIds.add(value);
                    }
                }
            }
        }
        
        return claimIds;
    }

    /**
     * Parses claim IDs from the first column of an Excel file.
     * Skips header row and empty cells.
     */
    private List<String> parseClaimIdsFromExcel(MultipartFile file) throws IOException {
        List<String> claimIds = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean isFirstRow = true;

            for (Row row : sheet) {
                // Skip header row
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }

                Cell cell = row.getCell(0);
                if (cell != null) {
                    String value = getCellValueAsString(cell);
                    if (value != null && !value.isBlank()) {
                        claimIds.add(value.trim());
                    }
                }
            }
        }

        return claimIds;
    }

    /**
     * Extracts cell value as String regardless of cell type.
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.STRING 
                    ? cell.getStringCellValue() 
                    : String.valueOf(cell.getNumericCellValue());
            default -> null;
        };
    }

    /**
     * Partitions a list into smaller sublists of specified size.
     */
    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}
