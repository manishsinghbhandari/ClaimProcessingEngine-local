package io.coredge.health.claims.service.AI;

import com.fasterxml.jackson.databind.JsonNode;
import io.coredge.health.claims.db.entity.ClaimAiReport;
import io.coredge.health.claims.db.entity.ClaimAiTrigger;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.AiNotes.ClaimAiReportRepository;
import io.coredge.health.claims.db.repositories.AiNotes.ClaimAiTriggerRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.app.RateLimitException;
import io.coredge.health.claims.models.claims.ClaimAiTriggerRequest;
import io.coredge.health.claims.models.claims.ClaimAiTriggerResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Automated service for triggering AI analysis on claims based on configurations
 * in the claim_ai_trigger table. Runs periodically to process pending triggers.
 */
@Slf4j
@Service
public class AutoClaimAiAnalysisService {

    @Autowired
    private ClaimAiTriggerRepository triggerRepository;

    @Autowired
    private ClaimAiReportRepository reportRepository;

    @Autowired
    private InsuranceClaimsRepository claimsRepository;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private io.coredge.health.claims.db.repositories.BenefitsMasterRepository benefitsMasterRepository;

    @Autowired
    private ClaimAINotesService claimAINotesService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ca.uhn.fhir.parser.IParser fhirParser;

    @Autowired
    private ca.uhn.fhir.rest.client.api.IGenericClient fhirClient;

    @Autowired
    private org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${ai.notes.auth-token}")
    private String aiAuthToken;

    @Value("${ai.notes.encryption-key}")
    private String aiEncryptionKey;

    @Value("${ai.notes.base-url}")
    private String defaultAiBaseUrl;

    @Value("${ai.notes.claim-bundle-uri}")
    private String defaultAiClaimUri;

    @Value("${claim.ai.analysis.scheduler.enable:false}")
    private boolean schedulerEnabled;

    @Value("${claim.ai.analysis.batch.size:1}")
    private int batchSize;

    @Value("${claim.ai.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${claim.ai.retry.initial-delay-ms:2000}")
    private long retryInitialDelayMs;

    private static final List<ExtensionStateCodeEnum> ALLOWED_STATES = List.of(
            ExtensionStateCodeEnum.CLINICAL_REVIEW,
            ExtensionStateCodeEnum.MANUAL_REVIEW,
            ExtensionStateCodeEnum.MEDICAL_REVIEW
    );

    private enum TriggerStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED;

        public String value() {
            return name();
        }
    }

    private static boolean alreadyRunning = false;

    private ExecutorService executorService;

    @PostConstruct
    void init() {
        this.executorService = Executors.newFixedThreadPool(batchSize);
    }
    /**
     * Manual task to process pending AI triggers.
     * Can be called via API endpoint to check for pending/in-progress triggers and process claims.
     */
//    retired this @Scheduled(fixedDelayString = "${claim.ai.analysis.scheduler.delay:60000}")
    @Scheduled(cron = "${claim.ai.analysis.scheduler.delay.cron:0/60 * * * * ?}")
    public void processAiTriggers() {
        log.info("Executing scheduled task for AI: Flag, {}", schedulerEnabled);
        if (!schedulerEnabled) {
            log.debug("Automated claim AI analysis scheduler is disabled");
            return;
        }

        if (alreadyRunning) {
            return;
        }

        alreadyRunning = true;
        log.info("Starting automated claim AI analysis scheduler");

        try {
            // Fetch all pending and in-progress triggers
            List<ClaimAiTrigger> pendingTriggers = triggerRepository.findPendingTriggers();
            List<ClaimAiTrigger> inProgressTriggers = triggerRepository.findInProgressTriggers();

            // Process pending triggers first, then in-progress ones
            pendingTriggers.forEach(this::processTrigger);
            inProgressTriggers.forEach(this::processTrigger);

            log.info("Completed automated claim AI analysis scheduler. Processed {} pending and {} in-progress triggers",
                    pendingTriggers.size(), inProgressTriggers.size());

        } catch (Exception e) {
            log.error("Error in automated claim AI analysis scheduler", e);
        } finally {
            alreadyRunning = false;
        }
    }

    /**
     * Process a single trigger configuration by fetching matching claims and submitting them to AI.
     *
     * @param trigger the trigger configuration
     */
    public void processTrigger(ClaimAiTrigger trigger) {
        boolean isWorkflow = Boolean.TRUE.equals(trigger.getIsWorkflow());

        log.info("Processing trigger ID: {} (isWorkflow={}) for package: {}, intervention: {}, date range: {} to {}",
                trigger.getId(), isWorkflow, trigger.getPackageCode(), trigger.getInterventionCode(),
                trigger.getStartDate(), trigger.getEndDate());

        try {
            // Update status to IN_PROGRESS if it's PENDING
            if (TriggerStatus.PENDING.value().equals(trigger.getStatus())) {
                trigger.setStatus(TriggerStatus.IN_PROGRESS.value());
                triggerRepository.save(trigger);
                log.info("Updated trigger {} status to IN_PROGRESS", trigger.getId());
            }

            // Fetch claims based on trigger criteria
            List<InsuranceClaim> claims = isWorkflow
                    ? fetchClaimsForTriggerWorkflow(trigger)
                    : fetchClaimsForTrigger(trigger);

            if (claims.isEmpty()) {
                log.info("No claims found for trigger {}. Marking as completed", trigger.getId());
                trigger.setStatus(TriggerStatus.COMPLETED.value());
                triggerRepository.save(trigger);
                return;
            }

            log.info("Found {} claims to process for trigger {}", claims.size(), trigger.getId());

            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            AtomicInteger processedCount = new AtomicInteger(0);

            try {
                List<Callable<Void>> tasks = new ArrayList<>();

                for (InsuranceClaim claim : claims) {
                    tasks.add(() -> {
                        // Acquire pessimistic lock in a short-lived transaction, then release the DB connection
                        // before making the slow AI HTTP call.
                        AtomicBoolean lockAcquired = new AtomicBoolean(false);
                        try {
                            txTemplate.executeWithoutResult(status -> {
                                var locked = claimsRepository.findByClaimIdForUpdateSkipLocked(claim.getClaimId());
                                if (locked.isEmpty()) {
                                    log.debug("Claim {} locked by another instance for trigger {}, skipping",
                                            claim.getClaimId(), trigger.getId());
                                    return;
                                }
                                lockAcquired.set(true);
                            });
                        } catch (Exception e) {
                            log.error("Error acquiring lock for claim {} trigger {}: {}",
                                    claim.getClaimId(), trigger.getId(), e.getMessage(), e);
                        }

                        // AI call happens outside the transaction — no DB connection held
                        if (lockAcquired.get()) {
                            try {
                                if (isWorkflow) {
                                    submitClaimToAiWorkflow(claim, trigger);
                                } else {
                                    submitClaimToAi(claim, trigger);
                                }
                                processedCount.incrementAndGet();
                            } catch (Exception e) {
                                log.error("Error processing claim {} for trigger {}: {}",
                                        claim.getClaimId(), trigger.getId(), e.getMessage(), e);
                            }
                        }
                        return null;
                    });
                }

                if (!tasks.isEmpty()) {
                    // BLOCKS until ALL synchronous calls finish
                    executorService.invokeAll(tasks, 1, TimeUnit.MINUTES);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Claim processing interrupted", e);
            }

            // Update trigger progress on main thread (avoids race conditions from executor threads)
            Long maxClaimNumber = claims.stream()
                    .map(InsuranceClaim::getClaimNumber)
                    .max(Long::compareTo)
                    .orElse(trigger.getLastProcessedClaimId());
            trigger.setLastProcessedClaimId(maxClaimNumber);
            trigger.setProcessedClaimsCount(trigger.getProcessedClaimsCount() + processedCount.get());
            triggerRepository.save(trigger);

            log.info("Batch complete for trigger {}. Processed {} claims. lastProcessedClaimId={}",
                    trigger.getId(), processedCount.get(), maxClaimNumber);

            // Check if there are more claims to process
            Long nextBatchCount = isWorkflow
                    ? fetchClaimsForTriggerWorkflowCount(trigger)
                    : fetchClaimsForTriggerCount(trigger);

            if (nextBatchCount == 0L) {
                trigger.setStatus(TriggerStatus.COMPLETED.value());
                triggerRepository.save(trigger);
                log.info("Completed trigger {}. No more claims to process.", trigger.getId());
            } else {
                log.info("Processed batch for trigger {}. {} more claims pending. Trigger remains IN_PROGRESS",
                        trigger.getId(), nextBatchCount);
            }

        } catch (Exception e) {
            log.error("Error processing trigger {}: {}", trigger.getId(), e.getMessage(), e);
            trigger.setStatus(TriggerStatus.FAILED.value());
            triggerRepository.save(trigger);
        }
    }

    /**
     * Fetch claims matching the trigger criteria.
     *
     * @param trigger the trigger configuration
     * @return list of matching claims
     */
    private List<InsuranceClaim> fetchClaimsForTrigger(ClaimAiTrigger trigger) {
        return fetchClaimsForTrigger(trigger, null);
    }

    private List<InsuranceClaim> fetchClaimsForTrigger(ClaimAiTrigger trigger, List<ExtensionStateCodeEnum> statuses) {
        LocalDateTime startDateTime = trigger.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = trigger.getEndDate().atTime(23, 59, 59);
        Long lastProcessedClaimId = trigger.getLastProcessedClaimId() != null ? trigger.getLastProcessedClaimId() : 0L;

        Pageable pageable = PageRequest.of(0, batchSize);

        // If intervention_code is specified, use it to filter claims
        if (trigger.getInterventionCode() != null && !trigger.getInterventionCode().isEmpty()) {
            return fetchClaimsByInterventionCode(
                    trigger.getInterventionCode(),
                    startDateTime,
                    endDateTime,
                    lastProcessedClaimId,
                    pageable,
                    statuses
            );
        }

        // Otherwise, fetch claims by package (using claim_items.code)
        return fetchClaimsByPackage(
                trigger.getPackageCode(),
                startDateTime,
                endDateTime,
                lastProcessedClaimId,
                pageable,
                statuses
        );
    }

    /**
     * Fetch claims matching the trigger criteria.
     *
     * @param trigger the trigger configuration
     * @return list of matching claims
     */
    private Long fetchClaimsForTriggerCount(ClaimAiTrigger trigger) {
        LocalDateTime startDateTime = trigger.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = trigger.getEndDate().atTime(23, 59, 59);
        Long lastProcessedClaimId = trigger.getLastProcessedClaimId() != null ? trigger.getLastProcessedClaimId() : 0L;

        Pageable pageable = PageRequest.of(0, batchSize);

        // If intervention_code is specified, use it to filter claims
        if (trigger.getInterventionCode() != null && !trigger.getInterventionCode().isEmpty()) {
            return fetchClaimsByInterventionCodeCount(
                    trigger.getInterventionCode(),
                    startDateTime,
                    endDateTime,
                    lastProcessedClaimId,
                    pageable
            );
        }

        // Otherwise, fetch claims by package (using claim_items.code)
        return fetchClaimsByPackageCount(
                trigger.getPackageCode(),
                startDateTime,
                endDateTime,
                lastProcessedClaimId,
                pageable
        );
    }

    /**
     * Fetch claims by intervention code.
     */
    private List<InsuranceClaim> fetchClaimsByInterventionCode(
            String interventionCode,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long lastProcessedClaimId,
            Pageable pageable,
            List<ExtensionStateCodeEnum> statuses) {

        log.debug("Fetching claims by intervention code: {} from {} to {} after claim ID {}",
                interventionCode, startDate, endDate, lastProcessedClaimId);

        // Fetch claim IDs that have the specified intervention code
        List<String> statusNames = (statuses != null) ? statuses.stream().map(Enum::name).toList() : null;
        List<Object[]> results = (statusNames != null && !statusNames.isEmpty())
                ? lineItemRepository.findClaimIdsByCodeAndDateRangeAndStatuses(
                        interventionCode, startDate, endDate, lastProcessedClaimId, statusNames, pageable)
                : lineItemRepository.findClaimIdsByCodeAndDateRange(
                        interventionCode, startDate, endDate, lastProcessedClaimId, pageable);

        if (results.isEmpty()) {
            return List.of();
        }

        // Extract UUIDs from Object[] results (each Object[] contains [claimId, claimNumber])
        List<UUID> claimIds = results.stream()
                .map(row -> (UUID) row[0])
                .toList();

        // Fetch actual claims
        return claimsRepository.findByClaimIdIn(claimIds)
                .orElse(List.of());
    }

    /**
     * Fetch claims by intervention code.
     */
    private Long fetchClaimsByInterventionCodeCount(
            String interventionCode,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long lastProcessedClaimId,
            Pageable pageable) {

        // Fetch claim IDs that have the specified intervention code
        return lineItemRepository.findClaimIdsCountByCodeAndDateRange(
                interventionCode,
                startDate,
                endDate,
                lastProcessedClaimId,
                pageable
        ).get().findFirst().orElse(0L);
    }

    /**
     * Fetch claims by package code.
     * Looks up all intervention codes belonging to the package from BenefitsMaster,
     * then queries claims using IN operator for accurate matching.
     */
    private List<InsuranceClaim> fetchClaimsByPackage(
            String packageCode,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long lastProcessedClaimId,
            Pageable pageable,
            List<ExtensionStateCodeEnum> statuses) {

        // Fetch all benefits belonging to this package code
        List<String> interventionCodes = benefitsMasterRepository.findByCode(packageCode)
                .map(benefitsList -> benefitsList.stream()
                        .map(io.coredge.health.claims.db.entity.BenefitsMaster::getInterventionCode)
                        .distinct()
                        .toList())
                .orElse(List.of());

        if (interventionCodes.isEmpty()) {
            log.info("No intervention codes found for package: {}", packageCode);
            return List.of();
        }
        log.info("Package {} resolved to {} intervention codes: {}. lastProcessedClaimId={}, dateRange={} to {}",
                packageCode, interventionCodes.size(), interventionCodes, lastProcessedClaimId, startDate, endDate);
        // Fetch claim IDs using IN operator for intervention codes
        List<String> statusNames = (statuses != null) ? statuses.stream().map(Enum::name).toList() : null;
        List<Object[]> results = (statusNames != null && !statusNames.isEmpty())
                ? lineItemRepository.findClaimIdsByCodesAndDateRangeAndStatuses(
                        interventionCodes, startDate, endDate, lastProcessedClaimId, statusNames, pageable)
                : lineItemRepository.findClaimIdsByCodesAndDateRange(
                        interventionCodes, startDate, endDate, lastProcessedClaimId, pageable);

        log.info("Query returned {} claim IDs for package {}", results.size(), packageCode);
        if (results.isEmpty()) {
            return List.of();
        }

        // Extract UUIDs from Object[] results (each Object[] contains [claimId, claimNumber])
        List<UUID> claimIds = results.stream()
                .map(row -> (UUID) row[0])
                .toList();

        // Fetch actual claims
        return claimsRepository.findByClaimIdIn(claimIds)
                .orElse(List.of());
    }

    /**
     * Fetch claims by package code.
     * Looks up all intervention codes belonging to the package from BenefitsMaster,
     * then queries claims using IN operator for accurate matching.
     */
    private Long fetchClaimsByPackageCount(
            String packageCode,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Long lastProcessedClaimId,
            Pageable pageable) {

        log.debug("Fetching claims by package: {} from {} to {} after claim ID {}",
                packageCode, startDate, endDate, lastProcessedClaimId);

        // Fetch all benefits belonging to this package code
        List<String> interventionCodes = benefitsMasterRepository.findByCode(packageCode)
                .map(benefitsList -> benefitsList.stream()
                        .map(io.coredge.health.claims.db.entity.BenefitsMaster::getInterventionCode)
                        .distinct()
                        .toList())
                .orElse(List.of());

        if (interventionCodes.isEmpty()) {
            log.warn("No intervention codes found for package: {}", packageCode);
            return 0L;
        }

        log.debug("Found {} intervention codes for package {}", interventionCodes.size(), packageCode);

        // Fetch claim IDs using IN operator for intervention codes
         return lineItemRepository.findClaimIdsCountByCodesAndDateRange(
                interventionCodes,
                startDate,
                endDate,
                lastProcessedClaimId,
                pageable
        ).get().findFirst().orElse(0L);
    }

    /**
     * Fetch claims for workflow mode
     */
    private List<InsuranceClaim> fetchClaimsForTriggerWorkflow(ClaimAiTrigger trigger) {
        return fetchClaimsForTrigger(trigger, ALLOWED_STATES);
    }

    private Long fetchClaimsForTriggerWorkflowCount(ClaimAiTrigger trigger) {
        Long totalCount = fetchClaimsForTriggerCount(trigger);
        if (totalCount == 0L) return 0L;
        return (long) fetchClaimsForTriggerWorkflow(trigger).size();
    }

    /**
     * Workflow mode: submit claim to AI via ClaimAINotesService.
     */
    private void submitClaimToAiWorkflow(InsuranceClaim claim, ClaimAiTrigger trigger) {
        String routeOverride = (trigger.getAiUrl() != null && !trigger.getAiUrl().trim().isEmpty())
                ? trigger.getAiUrl().trim()
                : null;
        claimAINotesService.submitClaimToAIEngine(claim.getClaimId(), routeOverride);
        log.info("Submitted claim {} to AI workflow with route url: {}", claim.getClaimId(), routeOverride);
    }

    /**
     * Submit a claim to the AI engine using trigger's custom AI URL or default endpoint.
     * This method is independent of ClaimAINotesService and stores results in claim_ai_reports.
     *
     * @param claim the claim to process
     * @param trigger the trigger configuration
     */
    private void submitClaimToAi(InsuranceClaim claim, ClaimAiTrigger trigger) {
        try {
            // Check if claim already has a report for this trigger (prevents duplicate processing across instances)
            if (reportRepository.existsByClaimIdAndTriggerId(claim.getClaimId(), trigger.getId())) {
                log.debug("Claim {} already has report for trigger {}, skipping", claim.getClaimId(), trigger.getId());
                return;
            }
            
            log.info("Submitting claim {} to AI engine for trigger {}", claim.getClaimId(), trigger.getId());
            
            // Check if claim has bundleId
            String bundleId = claim.getBundleId();
            if (bundleId == null || bundleId.trim().isEmpty()) {
                String error = "Claim " + claim.getClaimId() + " has no bundleId. Cannot fetch FHIR bundle";
                storeAiReport(claim, trigger, error, "FAILED");
                return;
            }
            
            // Fetch FHIR bundle for the claim
            Bundle fhirBundle = fhirClient.read().resource(Bundle.class).withId(bundleId).execute();
            
            if (fhirBundle == null) {
                String error = "FHIR bundle is null for bundleId: " + bundleId + ", claim: " + claim.getClaimId();
                storeAiReport(claim, trigger, error, "FAILED");
                return;
            }
            
            String bundleJson = fhirParser.encodeResourceToString(fhirBundle);
            // Determine AI URL: use trigger's custom URL if set, otherwise use default
            String aiUrl = determineAiUrl(trigger);
            
            // Submit to AI engine
            String responseJson = postClaimToAi(bundleJson, aiUrl);
            // Store response in claim_ai_reports table
            storeAiReport(claim, trigger, responseJson, "SUCCESS");
            
            log.info("Successfully submitted claim {} to AI engine at {}", 
                claim.getClaimId(), aiUrl);
            
        } catch (RateLimitException e) {
            // This allows the claim to be retried in the next scheduler run
            log.warn("Rate limited while submitting claim {} to AI engine. Will retry in next scheduler run.", 
                    claim.getClaimId());
        } catch (Exception e) {
            log.error("Error submitting claim {} to AI engine: {}", claim.getClaimId(), e.getMessage(), e);
            // Store error message in overall_notes, use FAILED status (matches constraint)
            storeAiReport(claim, trigger, "Failed to post claim to AI engine: " + e.getMessage(), "FAILED");
            // Don't rethrow - continue processing other claims
        }
    }
    /**
     * Determine which AI URL to use: trigger's custom URL or default.
     * If trigger's aiUrl is a full URL (http:// or https://), use it as-is.
     * If it's just a path/extension, append it to the base URL.
     */
    private String determineAiUrl(ClaimAiTrigger trigger) {
        if (trigger.getAiUrl() != null && !trigger.getAiUrl().trim().isEmpty()) {
            String customUrl = trigger.getAiUrl().trim();
            
            // Check if it's a full URL (starts with http:// or https://)
            if (customUrl.startsWith("http://") || customUrl.startsWith("https://")) {
                return customUrl;
            }
            
            // It's just a path/extension, append to base URL
            String baseUrl = defaultAiBaseUrl.endsWith("/") ? defaultAiBaseUrl : defaultAiBaseUrl + "/";
            String path = customUrl.startsWith("/") ? customUrl.substring(1) : customUrl;
            String fullUrl = baseUrl + path;

            return fullUrl;
        }
        
        // Build default URL
        String baseUrl = defaultAiBaseUrl.endsWith("/") ? defaultAiBaseUrl : defaultAiBaseUrl + "/";
        String uri = defaultAiClaimUri.startsWith("/") ? defaultAiClaimUri.substring(1) : defaultAiClaimUri;
        String defaultUrl = baseUrl + uri;
        
        log.debug("Using default AI URL: {}", defaultUrl);
        return defaultUrl;
    }

    /**
     * Post claim bundle to AI engine at specified URL.
     * Note: This method is called from ExecutorService threads (not scheduler thread directly),
     * so blocking is acceptable here. The scheduler delegates to a thread pool via invokeAll().
     */
    private String postClaimToAi(String bundleJson, String aiUrl) {
        org.springframework.web.reactive.function.client.WebClient webClient = webClientBuilder
                .baseUrl(aiUrl)
                .defaultHeader(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + aiAuthToken)
                .defaultHeader("X-Encryption-Key", aiEncryptionKey)
                .build();

        try {
            return webClient.post()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(bundleJson)
                    .retrieve()
                    .onStatus(
                            status -> status == org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                            response -> reactor.core.publisher.Mono.error(new RateLimitException("429 Too Many Requests from AI engine", null))
                    )
                    .bodyToMono(String.class)
                    .retryWhen(
                            reactor.util.retry.Retry.backoff(maxRetryAttempts - 1, java.time.Duration.ofMillis(retryInitialDelayMs))
                                    .filter(ex -> ex instanceof RateLimitException)
                                    .doBeforeRetry(rs -> log.warn("429 Too Many Requests from AI engine. Retrying attempt {}...", rs.totalRetries() + 1))
                                    .onRetryExhaustedThrow((spec, rs) -> {
                                        log.warn("429 Too Many Requests from AI engine after {} attempts. Giving up.", maxRetryAttempts);
                                        return new RateLimitException("Rate limited by AI engine after " + maxRetryAttempts + " retries", rs.failure());
                                    })
                    )
                    .block();
        } catch (Exception e) {
            if (e instanceof RateLimitException) {
                throw (RateLimitException) e;
            }
            log.error("Error posting claim to AI at {}: {}", aiUrl, e.getMessage(), e);
            throw new RuntimeException("Failed to post claim to AI engine", e);
        }
    }

    /**
     * Store AI analysis report in claim_ai_reports table.
     * Parses the AI response JSON and extracts scores, documentNotes, and overallNotes.
     * Stores notes and scores as String (JSONB columns with @JdbcTypeCode).
     */
    private void storeAiReport(InsuranceClaim claim, ClaimAiTrigger trigger, String responseJson, String status) {
        try {
            String notes = null;
            String overallNotes = null;
            String scores = null;

            // Parse AI response JSON if available
            if (responseJson != null && !responseJson.isEmpty() && !status.startsWith("FAILED")) {
                try {
                    JsonNode rootNode = objectMapper.readTree(responseJson);
                    
                    // Extract documentNotes array and serialize to JSON string
                    if (rootNode.has("documentNotes")) {
                        JsonNode documentNotesNode = rootNode.get("documentNotes");
                        if (documentNotesNode.isArray() && documentNotesNode.size() > 0) {
                            notes = objectMapper.writeValueAsString(documentNotesNode);
                        }
                    }
                    
                    // Extract overallNotes as plain text
                    if (rootNode.has("overallNotes")) {
                        overallNotes = rootNode.get("overallNotes").asText();
                    }
                    
                    // Extract scores object and serialize to JSON string
                    if (rootNode.has("scores")) {
                        scores = objectMapper.writeValueAsString(rootNode.get("scores"));
                    }
                    
                } catch (Exception e) {
                    log.warn("Failed to parse AI response JSON for claim {}: {}. Storing as text in overall_notes",
                            claim.getClaimId(), e.getMessage());
                    overallNotes = responseJson;
                }
            } else if (status.startsWith("FAILED")) {
                overallNotes = responseJson != null ? responseJson : "Failed to get response from AI";
            }

            ClaimAiReport report = ClaimAiReport.builder()
                    .claim(claim)
                    .trigger(trigger)
                    .status(status)
                    .notes(notes)
                    .overallNotes(overallNotes)
                    .scores(scores)
                    .build();
            
            reportRepository.save(report);
            log.debug("Stored AI report for claim {} with status: {}", claim.getClaimId(), status);
            
        } catch (Exception e) {
            log.error("Error storing AI report for claim {}", claim.getClaimId(), e);
            // Don't throw - report storage failure shouldn't stop processing
        }
    }

    // ==================== ClaimAiTrigger CRUD Operations ====================

    /**
     * Create a new AI trigger configuration.
     */
    public String createTrigger(ClaimAiTriggerRequest request) {
        validateTriggerRequest(request);

        ClaimAiTrigger trigger = ClaimAiTrigger.builder()
                .packageCode(request.getPackageCode())
                .interventionCode(request.getInterventionCode())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status("PENDING")
                .processedClaimsCount(0)
                .aiUrl(request.getAiUrl())
                .isWorkflow(Boolean.TRUE.equals(request.getIsWorkflow()))
                .build();

        ClaimAiTrigger savedTrigger = triggerRepository.save(trigger);
        log.info("Created AI trigger with ID: {}", savedTrigger.getId());

        return "AI trigger created successfully";
    }

    /**
     * Get trigger by ID.
     */
    public ClaimAiTriggerResponse getTriggerById(Long id) {
        ClaimAiTrigger trigger = triggerRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("AI trigger not found with ID: " + id));
        return mapToResponse(trigger);
    }

    /**
     * Get all triggers.
     */
    public List<ClaimAiTriggerResponse> getAllTriggers() {
        return triggerRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update an existing trigger.
     * Can also be used for soft delete by updating status to 'DELETED' or 'CANCELLED'.
     */
    public ClaimAiTriggerResponse updateTrigger(Long id, ClaimAiTriggerRequest request) {
        validateTriggerRequest(request);

        ClaimAiTrigger trigger = triggerRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("AI trigger not found with ID: " + id));

        // Only allow updates if trigger is not currently IN_PROGRESS
        if (TriggerStatus.IN_PROGRESS.value().equals(trigger.getStatus())) {
            throw new BadRequestException("Cannot update trigger that is currently in progress");
        }

        trigger.setPackageCode(request.getPackageCode());
        trigger.setInterventionCode(request.getInterventionCode());
        trigger.setStartDate(request.getStartDate());
        trigger.setEndDate(request.getEndDate());
        trigger.setAiUrl(request.getAiUrl());
        trigger.setIsWorkflow(Boolean.TRUE.equals(request.getIsWorkflow()));
        trigger.setStatus(TriggerStatus.PENDING.value()); // Reset to PENDING on update

        ClaimAiTrigger updatedTrigger = triggerRepository.save(trigger);
        log.info("Updated AI trigger ID: {}", updatedTrigger.getId());

        return mapToResponse(updatedTrigger);
    }

    /**
     * Validate trigger request.
     */
    private void validateTriggerRequest(ClaimAiTriggerRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new BadRequestException("Start date and end date are required");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date must be after start date");
        }

        if (request.getPackageCode() == null) {
            throw new BadRequestException("Either package code must be provided");
        }
    }

    /**
     * Map entity to response DTO.
     */
    private ClaimAiTriggerResponse mapToResponse(ClaimAiTrigger trigger) {
        return ClaimAiTriggerResponse.builder()
                .id(trigger.getId())
                .packageCode(trigger.getPackageCode())
                .interventionCode(trigger.getInterventionCode())
                .startDate(trigger.getStartDate())
                .endDate(trigger.getEndDate())
                .status(trigger.getStatus())
                .lastProcessedClaimId(trigger.getLastProcessedClaimId())
                .processedClaimsCount(trigger.getProcessedClaimsCount())
                .aiUrl(trigger.getAiUrl())
                .isWorkflow(trigger.getIsWorkflow())
                .createdBy(trigger.getCreatedBy())
                .createdDate(trigger.getCreationDate())
                .lastModifiedBy(trigger.getLastModifiedBy())
                .lastModifiedDate(trigger.getLastModifiedDate())
                .build();
    }
}
