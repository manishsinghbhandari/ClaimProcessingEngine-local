package io.coredge.health.claims.rest.impl;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.PhcBatchPayment;
import io.coredge.health.claims.dto.phc.*;
import jakarta.validation.Valid;
import io.coredge.health.claims.service.PhcPaymentApprovalService;
import io.coredge.health.claims.service.PhcBackgroundProcessingService;
import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingAction;
import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.exception.app.BadRequestException;

import java.time.LocalDate;

@CrossOrigin
@Slf4j
@RestController
@RequestMapping(Constants.EndPoints.PHC_PAYMENT_API_PATH)
public class PhcPaymentApprovalControllerImpl {

    @Autowired
    private PhcPaymentApprovalService phcPaymentApprovalService;

    @Autowired
    private PhcBackgroundProcessingService phcBackgroundProcessingService;

    /**
     * Get batch payments with filters
     */
    @GetMapping(path = Constants.EndPoints.PHC_PAYMENT_BATCH)
    public ResponseEntity<CollectionModel<EntityModel<PhcBatchPaymentDto>>> getBatchPayments(
            @RequestParam(required = false) PhcBatchPayment.Status status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String countyName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        PagedModel<EntityModel<PhcBatchPaymentDto>> batchPayments = phcPaymentApprovalService.getBatchPaymentsFiltered(status, startDate, endDate, countyName, pageable);
        return ResponseEntity.ok(batchPayments);
    }

    /**
     * View batch payment details by ID
     */
    @GetMapping(path = Constants.EndPoints.PHC_PAYMENT_VIEW_BATCH)
    public ResponseEntity<PhcBatchPaymentDto> viewBatchPayment(@PathVariable Long batchId) {
        PhcBatchPaymentDto batchPayment = phcPaymentApprovalService.getBatchPaymentDetails(batchId);
        return ResponseEntity.ok(batchPayment);
    }

    /**
     * Get facility payments (phc_claim_payments) linked to batch payment by ID
     */
    @GetMapping(path = Constants.EndPoints.PHC_PAYMENT_BATCH_BY_ID)
    public ResponseEntity<Page<PhcClaimPaymentDto>> getFacilityPaymentsByBatchId(
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<PhcClaimPaymentDto> facilityPayments = phcPaymentApprovalService.getFacilityPayments(batchId, null, null, pageable);
        return ResponseEntity.ok(facilityPayments);
    }


    /**
     * Get facility payments for batch
     */
    @GetMapping(path = Constants.EndPoints.PHC_PAYMENT_FACILITY_BY_BATCH)
    public ResponseEntity<Page<PhcClaimPaymentDto>> getFacilityPaymentsByBatch(
            @PathVariable Long batchId,
            @RequestParam(required = false) String providerName,
            @RequestParam(required = false) String mflCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PhcClaimPaymentDto> facilityPayments = phcPaymentApprovalService.getFacilityPayments(batchId, providerName, mflCode, pageable);
        return ResponseEntity.ok(facilityPayments);
    }

    /**
     * Get facility payment by ID
     */
    @GetMapping(path = Constants.EndPoints.PHC_PAYMENT_FACILITY_BY_ID)
    public ResponseEntity<PhcClaimPaymentDto> getFacilityPaymentById(@PathVariable Long facilityPaymentId) {
        PhcClaimPaymentDto facilityPayment = phcPaymentApprovalService.getFacilityPaymentDetails(facilityPaymentId);
        return ResponseEntity.ok(facilityPayment);
    }

    /**
     * Trigger manual PHC payment calculation (cursor-based)
     */
    @PostMapping(path = Constants.EndPoints.PHC_PAYMENT_CALCULATION)
    public ResponseEntity<String> calculatePhcPayments(
            @RequestBody @Valid PhcProcessingRequestDto request) {
        
        PhcCursorResponseDto response = phcBackgroundProcessingService.initializeCalculationCursor(request.getMonthName());
        return ResponseEntity.ok(response.getMessage());
    }

    /**
     * Process facility payment approval (cursor-based)
     */
    @PostMapping(path = Constants.EndPoints.PHC_PAYMENT_FACILITY_ACTION)
    public ResponseEntity<String> processFacilityPaymentAction(
            @RequestBody @Valid PhcProcessingRequestDto request) {
        
        PhcCursorResponseDto response = phcBackgroundProcessingService.initializeApprovalCursor(request.getMonthName());
        return ResponseEntity.ok(response.getMessage());
    }

    /**
     * Send approved batch payments to ERP (cursor-based)
     */
    @PostMapping(path = Constants.EndPoints.PHC_PAYMENT_ERP_SUBMIT)
    public ResponseEntity<String> sendBatchPaymentToErp(
            @RequestBody @Valid PhcProcessingRequestDto request) {
        
        PhcCursorResponseDto response = phcBackgroundProcessingService.initializeErpSubmissionCursor(request.getMonthName());
        return ResponseEntity.ok(response.getMessage());
    }

    /**
     * Mark batch payment as completed
     */
    @PostMapping(path = Constants.EndPoints.PHC_MARK_BATCH_PAYMENT_COMPLETED)
    public ResponseEntity<String> markBatchPaymentCompleted(
            @RequestBody PhcBatchCompletionRequest completionRequest) {
        
        String result = phcPaymentApprovalService.markBatchPaymentCompleted(
                completionRequest.getBatchId(), 
                HealthIdContextHolder.userId(), 
                completionRequest.getRemarks());
        return ResponseEntity.ok(result);
    }

    /**
     * Get claims for a specific provider payment
     */
    @GetMapping(path = Constants.EndPoints.PHC_PAYMENT_REPORTS_CLAIMS)
    public ResponseEntity<PhcClaimsSummaryResponseDto> getClaimsByPaymentId(
            @PathVariable("paymentId") Long paymentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        PhcClaimsSummaryResponseDto result = (page != null || size != null)
                ? phcPaymentApprovalService.getClaimsByPaymentId(paymentId, page, size)
                : phcPaymentApprovalService.getClaimsByPaymentId(paymentId);
        return ResponseEntity.ok(result);
    }

    /**
     * Export county batch summaries to ZIP file (CSV format)
     */
    @GetMapping(path = Constants.EndPoints.PHC_PAYMENT_REPORTS_EXPORT_BATCHES)
    public ResponseEntity<Resource> exportBatchesByMonth(
            @PathVariable("month") String month) {
        
        Map<String, Object> fileData = phcPaymentApprovalService.exportBatchesByMonth(month);
        
        ByteArrayResource resource = (ByteArrayResource) fileData.get("stream");
        String fileName = (String) fileData.get("fileName");
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * Export provider payments to ZIP file (CSV format)
     */
    @GetMapping(path = Constants.EndPoints.PHC_PAYMENT_REPORTS_EXPORT_PROVIDERS)
    public ResponseEntity<Resource> exportProvidersByBatch(
            @PathVariable("batchId") Long batchId) {
        
        Map<String, Object> fileData = phcPaymentApprovalService.exportProvidersByBatch(batchId);
        
        ByteArrayResource resource = (ByteArrayResource) fileData.get("stream");
        String fileName = (String) fileData.get("fileName");
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * Export claims for a specific provider payment to ZIP (CSV format)
     */
    @GetMapping(path = Constants.EndPoints.PHC_PAYMENT_REPORTS_EXPORT_CLAIMS)
    public ResponseEntity<Resource> exportClaimsByPayment(
            @PathVariable("paymentId") Long paymentId) {

        Map<String, Object> fileData = phcPaymentApprovalService.exportClaimsByPayment(paymentId);

        ByteArrayResource resource = (ByteArrayResource) fileData.get("stream");
        String fileName = (String) fileData.get("fileName");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * Export all facility payments for a specific month (all counties) to ZIP (CSV format)
     */
    @GetMapping(path = Constants.EndPoints.PHC_PAYMENT_REPORTS_EXPORT_FACILITIES)
    public ResponseEntity<Resource> exportFacilityReportByMonth(
            @PathVariable("yearMonth") String yearMonth) {

        Map<String, Object> fileData = phcPaymentApprovalService.exportFacilityReportByMonth(yearMonth);

        ByteArrayResource resource = (ByteArrayResource) fileData.get("stream");
        String fileName = (String) fileData.get("fileName");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * Export all claims for a specific batch to ZIP (CSV format) with enhanced columns
     */
    @GetMapping(path = Constants.EndPoints.PHC_PAYMENT_REPORTS_EXPORT_CLAIMS_BY_BATCH)
    public ResponseEntity<Resource> exportClaimsSummaryByBatch(
            @PathVariable("batchId") Long batchId) {

        Map<String, Object> fileData = phcPaymentApprovalService.exportClaimsSummaryByBatch(batchId);

        ByteArrayResource resource = (ByteArrayResource) fileData.get("stream");
        String fileName = (String) fileData.get("fileName");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // ==================== CURSOR MONITORING & CONTROL ENDPOINTS ====================

    /**
     * Get background processing cursor status by ID
     * Used to monitor progress of ongoing PHC processing
     */
    @GetMapping(path = Constants.EndPoints.PHC_BACKGROUND_PROCESS_STATUS)
    public ResponseEntity<PhcCursorStatusDto> getBackgroundProcessStatus(
            @PathVariable("cursorId") Long cursorId) {
        
        PhcCursorStatusDto status = phcBackgroundProcessingService.getCursorStatus(cursorId);
        return ResponseEntity.ok(status);
    }

    /**
     * List all background processes with optional filters
     * Supports filtering by yearMonth, action, and status
     */
    @GetMapping(path = Constants.EndPoints.PHC_BACKGROUND_PROCESSES_LIST)
    public ResponseEntity<Page<PhcCursorStatusDto>> listBackgroundProcesses(
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) ProcessingAction action,
            @RequestParam(required = false) ProcessingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "creationDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<PhcCursorStatusDto> processes = phcBackgroundProcessingService.listCursors(yearMonth, action, status, pageable);
        return ResponseEntity.ok(processes);
    }

    /**
     * Retry a failed background process
     * Resets the cursor to PENDING status for scheduler to pick up
     */
    @PostMapping(path = Constants.EndPoints.PHC_BACKGROUND_PROCESS_RETRY)
    public ResponseEntity<String> retryBackgroundProcess(
            @PathVariable("cursorId") Long cursorId) {
        
        String result = phcBackgroundProcessingService.retryCursor(cursorId);
        return ResponseEntity.ok(result);
    }

    /**
     * Cancel a pending or in-progress background process
     * Sets the cursor to PAUSED status
     */
    @PostMapping(path = Constants.EndPoints.PHC_BACKGROUND_PROCESS_CANCEL)
    public ResponseEntity<String> cancelBackgroundProcess(
            @PathVariable("cursorId") Long cursorId) {
        
        String result = phcBackgroundProcessingService.cancelCursor(cursorId);
        return ResponseEntity.ok(result);
    }
}
