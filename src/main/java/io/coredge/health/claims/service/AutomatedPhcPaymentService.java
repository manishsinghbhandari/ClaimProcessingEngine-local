package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.AdminLevelPopulation;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.PhcBatchPayment;
import io.coredge.health.claims.db.entity.PhcClaimPayment;
import io.coredge.health.claims.db.entity.PhcProcessingCursor;
import io.coredge.health.claims.db.repositories.AdminLevelOneRepository;
import io.coredge.health.claims.db.repositories.AdminLevelPopulationRepository;
import io.coredge.health.claims.db.repositories.IcdCodesRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.PhcBatchPaymentRepository;
import io.coredge.health.claims.db.repositories.PhcClaimPaymentRepository;
import io.coredge.health.claims.db.repositories.PhcProcessingCursorRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.phccalculation.CountyStatDto;
import io.coredge.health.claims.models.phccalculation.PHCClaimsDbDto;
import io.coredge.health.claims.service.phc.PhcPopulationStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Automated PHC Payment Service
 * 
 * Runs on the 28th of each month to calculate previous month's PHC payments
 * Stores results in phc_batch_payments (county level) and phc_claim_payments (facility level)
 * Updates claims table with facility payment ID for tracking
 * 
 * Reuses existing PHC calculation logic without interfering with current MongoDB process
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomatedPhcPaymentService {

    private final InsuranceClaimsRepository insuranceClaimsRepository;
    private final PhcBatchPaymentRepository phcBatchPaymentRepository;
    private final PhcClaimPaymentRepository phcClaimPaymentRepository;
    private final PhcProcessingCursorRepository cursorRepository;
    private final AdminLevelPopulationRepository adminLevelPopulationRepository;
    private final IcdCodesRepository icdCodeRepository;
    private final AdminLevelOneRepository adminLevelOneRepository;
    private final PhcPopulationStatsService phcPopulationStatsService;
    private final CountyPopulationService countyPopulationService;

    @Value("${phc.per.head.amount}")
    private BigDecimal phcPerHeadAmount;

    @Value("${phc.scheduler.calculation.county.delay:30000}")
    private long countyProcessingDelay;

    // Automated calculation now handled by PhcProcessingScheduler + cursor-based approach

    /**
     * Cursor-based PHC payment calculation with batching, checkpoints, and retry support.
     * - Incremental processing (county-by-county) instead of all claims at once
     * - Progress tracking with real-time updates
     * - Checkpoint support for resume on failure
     * - Error isolation per county
     *
     */
    public void calculatePhcPaymentsWithCursor(PhcProcessingCursor cursor, YearMonth targetMonth, String monthName) {
        log.info("Starting cursor-based PHC calculation for month: {} with cursor ID: {}", monthName, cursor.getId());

        try {
            // Get date range for the month
            LocalDateTime[] dateRange = Helper.getStartAndEndDateTime(Helper.decodeValue(monthName));
            LocalDateTime startDateTime = dateRange[0];
            LocalDateTime endDateTime = dateRange[1];

            // Step 1: Get population data first (needed to know which counties to process)
            List<AdminLevelPopulation> countyPopulations = getOrFetchPopulationData(targetMonth, monthName);
            if (countyPopulations.isEmpty()) {
                log.warn("No county population data found for month: {}", monthName);
                cursor.setTotalRecords(0);
                return;
            }

            Map<String, BigDecimal> countyPopulationMap = countyPopulations.stream()
                    .collect(Collectors.toMap(cp -> cp.getAdminLevelEntity().getName(), cp -> BigDecimal.valueOf(cp.getPopulation())));

            Map<String, AdminLevelPopulation> countyPopulationData = countyPopulations.stream()
                    .collect(Collectors.toMap(cp -> cp.getAdminLevelEntity().getName(), cp -> cp));

            // Step 2: Prepare ICD code maps (one-time setup for all counties)
            Map<String, BigDecimal> icd10Codes = new HashMap<>();
            Map<String, BigDecimal> icd11Codes = new HashMap<>();
            populateIcdCodeMaps(icd10Codes, icd11Codes);

            // Step 3: Process each county separately (fetch claims per county for memory optimization)
            processCountiesSeparately(cursor, targetMonth, monthName, startDateTime, endDateTime,
                    countyPopulationMap, countyPopulationData, icd10Codes, icd11Codes);

            log.info("Successfully completed cursor-based PHC calculation for month: {}", monthName);

        } catch (Exception e) {
            log.error("Error in cursor-based PHC calculation for month {}: {}", monthName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Process counties separately to minimize memory footprint.
     * Fetches claims per county instead of loading all claims at once.
     *
     * @param cursor The processing cursor for progress tracking
     * @param targetMonth The target month for payment calculation
     * @param monthName The month name in format "MMM yyyy"
     * @param startDateTime Start of the date range
     * @param endDateTime End of the date range
     * @param countyPopulationMap Map of county name to population count
     * @param countyPopulationData Map of county populations with admin level links
     * @param icd10Codes Map of ICD-10 codes to weights
     * @param icd11Codes Map of ICD-11 codes to weights
     */
    private void processCountiesSeparately(
            PhcProcessingCursor cursor,
            YearMonth targetMonth,
            String monthName,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            Map<String, BigDecimal> countyPopulationMap,
            Map<String, AdminLevelPopulation> countyPopulationData,
            Map<String, BigDecimal> icd10Codes,
            Map<String, BigDecimal> icd11Codes) {

        int totalCounties = countyPopulationMap.size();
        int processedCounties = 0;
        int totalFacilityPayments = 0;
        int processedFacilityPayments = 0;

        log.info("Processing {} counties separately for memory optimization", totalCounties);

        // Resume logic: Skip already processed counties if cursor has checkpoint
        boolean isResuming = cursor.getLastProcessedBatchId() != null;
        if (isResuming) {
            log.info("Resuming from checkpoint - last processed batch ID: {}", cursor.getLastProcessedBatchId());
        }

        // Process each county
        for (Map.Entry<String, BigDecimal> entry : countyPopulationMap.entrySet()) {
            String countyName = entry.getKey();
            BigDecimal countyPopulation = entry.getValue();

            try {
                // Skip county if resuming and batch payment already exists
                if (isResuming) {
                    AdminLevelPopulation countyPopData = countyPopulationData.get(countyName);
                    if (countyPopData != null) {
                        LocalDate paymentDate = targetMonth.atDay(1);
                        boolean countyAlreadyProcessed = phcBatchPaymentRepository
                                .existsByAdminLevelPopulationIdAndDate(countyPopData.getId(), paymentDate);

                        if (countyAlreadyProcessed) {
                            log.info("Skipping already processed county {}/{}: {} (resuming from checkpoint)",
                                    processedCounties + 1, totalCounties, countyName);
                            processedCounties++;
                            continue;
                        }
                    }
                }

                log.info("Fetching claims for county {}/{}: {}", processedCounties + 1, totalCounties, countyName);

                // Fetch claims for this county only (memory optimized)
                List<PHCClaimsDbDto> countyClaims = insuranceClaimsRepository
                        .findPhcClaimsDtosDatesFilterByCounty(ExtensionStateCodeEnum.APPROVED, startDateTime, endDateTime, countyName)
                        .orElseGet(Collections::emptyList);

                if (countyClaims.isEmpty()) {
                    log.info("No approved claims found for county: {}. Skipping.", countyName);
                    continue;
                }

                log.info("Found {} claims for county: {}", countyClaims.size(), countyName);

                // Get ICD codes for this county's claims
                Map<String, List<String>> claimsIcdMap = getClaimIcdCodes(countyClaims);

                // Filter by valid ICD codes
                List<PHCClaimsDbDto> validClaims = filterClaimsByIcdCodes(countyClaims, claimsIcdMap, icd10Codes, icd11Codes);

                if (validClaims.isEmpty()) {
                    log.warn("No valid claims (ICD filter) for county: {}. Skipping.", countyName);
                    continue;
                }

                log.info("{} valid claims after ICD filtering for county: {}", validClaims.size(), countyName);

                // Process county in separate transaction (commits immediately)
                int facilityPaymentCount = processSingleCountyWithTransaction(
                        countyName, validClaims, countyPopulation, claimsIcdMap, 
                        icd10Codes, icd11Codes, targetMonth, countyPopulationData, cursor);

                totalFacilityPayments += facilityPaymentCount;
                processedFacilityPayments += facilityPaymentCount;
                processedCounties++;

                // Update cursor progress
                cursor.setProcessedRecords(processedFacilityPayments);
                cursor.setTotalRecords(totalFacilityPayments);
                cursorRepository.save(cursor);

                log.info("Completed county {}/{}: {} ({} facility payments created, {} claims processed)",
                        processedCounties, totalCounties, countyName, facilityPaymentCount, validClaims.size());

                // Clear county claims from memory
                countyClaims.clear();
                validClaims.clear();
                claimsIcdMap.clear();

                // Add delay before processing next county (except for last county)
                if (processedCounties < totalCounties) {
                    try {
                        log.debug("Applying {} ms delay before processing next county", countyProcessingDelay);
                        Thread.sleep(countyProcessingDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("County processing delay interrupted");
                    }
                }

            } catch (Exception e) {
                log.error("Error processing county {}: {}", countyName, e.getMessage(), e);
                cursor.setFailedRecords(cursor.getFailedRecords() + 1);
                cursorRepository.save(cursor);
            }
        }
    }

    /**
     * Process a single county with transaction boundary.
     * This allows each county's data to be committed independently.
     */
    private int processSingleCountyWithTransaction(
            String countyName,
            List<PHCClaimsDbDto> validClaims,
            BigDecimal countyPopulation,
            Map<String, List<String>> claimsIcdMap,
            Map<String, BigDecimal> icd10Codes,
            Map<String, BigDecimal> icd11Codes,
            YearMonth targetMonth,
            Map<String, AdminLevelPopulation> countyPopulationData,
            PhcProcessingCursor cursor) {

        // Calculate payment data for this county
        CountyPaymentData paymentData = calculateCountyPaymentData(
                validClaims, countyPopulation, claimsIcdMap, icd10Codes, icd11Codes);

        // Create batch payment (county level)
        PhcBatchPayment batchPayment = createBatchPayment(countyName, paymentData, targetMonth, countyPopulationData);

        // Update cursor with batch checkpoint
        cursor.setLastProcessedBatchId(batchPayment.getId());

        // Create facility payments and update claims
        createFacilityPaymentsForCounty(validClaims, paymentData, batchPayment, cursor);

        int facilityPaymentCount = paymentData.facilityPayments.size();
        log.info("Transaction committed for county: {} ({} facility payments created)",
                countyName, facilityPaymentCount);

        return facilityPaymentCount;
    }

    /**
     * Get or fetch population data with fallback to external API.
     * First attempts to retrieve from local database, then falls back to external Safaricom API if not found.
     *
     * @param targetMonth The target month for population data
     * @param monthName The month name in format "MMM yyyy"
     * @return List of county populations with admin level data
     * @throws BadRequestException if no population data available from both sources
     */
    private List<AdminLevelPopulation> getOrFetchPopulationData(YearMonth targetMonth, String monthName) {
        List<AdminLevelPopulation> countyPopulations = adminLevelPopulationRepository.findAllByMonth(monthName);

        if (countyPopulations.isEmpty()) {
            log.warn("No county population data found for month: {}. Fetching from external API.", monthName);
            countyPopulations = fetchAndSavePopulationDataFromApi(targetMonth, monthName);
            
            if (countyPopulations.isEmpty()) {
                throw new BadRequestException("No county population data available for month: " + monthName);
            }
        }

        return countyPopulations;
    }

    /**
     * Create a single batch payment for a county.
     * Links to specific county admin level from population data.
     *
     * @param countyName The name of the county
     * @param paymentData The calculated payment data for the county
     * @param targetMonth The month for the payment
     * @param countyPopulationData Map of county populations with admin level links
     * @return The created and saved batch payment entity
     * @throws BadRequestException if admin level entity not found for county
     */
    private PhcBatchPayment createBatchPayment(
            String countyName,
            CountyPaymentData paymentData,
            YearMonth targetMonth,
            Map<String, AdminLevelPopulation> countyPopulationData) {

        AdminLevelPopulation countyPopulation = countyPopulationData.get(countyName);
        if (countyPopulation == null || countyPopulation.getAdminLevelEntity() == null) {
            throw new BadRequestException("No admin level entity found for county: " + countyName);
        }

        // Use 28th to match cursor batch_preparation_date
        LocalDate paymentDate = targetMonth.atDay(28);
        Long adminLevelPopId = countyPopulation.getId();

        // Check if batch payment already exists for this county and month
        Optional<PhcBatchPayment> existingBatch = phcBatchPaymentRepository
                .findByAdminLevelPopulationIdAndDate(adminLevelPopId, paymentDate);

        if (existingBatch.isPresent()) {
            log.info("Batch payment already exists for county: {} (ID: {}). Skipping creation.",
                    countyName, existingBatch.get().getId());
            return existingBatch.get();
        }

        PhcBatchPayment batchPayment = PhcBatchPayment.builder()
                .adminLevelPopulation(countyPopulation)
                .date(paymentDate)
                .amount(paymentData.totalAmount)
                .approvedAmount(null)
                .paidAmount(BigDecimal.ZERO)
                .numberOfClaims((long) paymentData.totalClaimCount)
                .globalBudget(paymentData.totalAmount)
                .status(PhcBatchPayment.Status.PENDING)
                .remarks("Auto calculated")
                .build();

        batchPayment = phcBatchPaymentRepository.save(batchPayment);
        log.info("Created batch payment for county: {} with amount: {} linked to admin level: {}",
                countyName, paymentData.totalAmount, countyName);

        return batchPayment;
    }

    /**
     * Create facility payments for a county and update claims with payment IDs.
     * Updates cursor checkpoint after each facility payment for resumability.
     *
     * @param countyClaims List of claims for this county
     * @param paymentData County payment data with facility breakdown
     * @param batchPayment The parent batch payment entity
     * @param cursor The processing cursor for checkpoint updates
     * @return Number of claims successfully updated with payment IDs
     */
    private int createFacilityPaymentsForCounty(
            List<PHCClaimsDbDto> countyClaims,
            CountyPaymentData paymentData,
            PhcBatchPayment batchPayment,
            PhcProcessingCursor cursor) {

        int updatedClaims = 0;

        for (Map.Entry<Long, FacilityPaymentData> entry : paymentData.facilityPayments.entrySet()) {
            FacilityPaymentData facilityData = entry.getValue();

            // Check if facility payment already exists for this provider and batch
            Optional<PhcClaimPayment> existingPayment = phcClaimPaymentRepository
                    .findByProviderIdAndPhcBatchPayment(facilityData.providerId, batchPayment);

            PhcClaimPayment facilityPayment;
            if (existingPayment.isPresent()) {
                facilityPayment = existingPayment.get();
                log.info("Facility payment already exists for provider: {} (ID: {}). Skipping creation.",
                        facilityData.providerId, facilityPayment.getId());
            } else {
                // Create facility payment
                facilityPayment = PhcClaimPayment.builder()
                        .phcBatchPayment(batchPayment)
                        .aggregatedClaimId(UUID.randomUUID().toString())
                        .providerId(facilityData.providerId)
                        .amount(facilityData.amount)
                        .numberOfClaims(Double.valueOf(facilityData.numberOfClaims))
                        .batchPreparationDate(batchPayment.getDate())
                        .status(ExtensionStateCodeEnum.PENDING.name())
                        .build();

                facilityPayment = phcClaimPaymentRepository.save(facilityPayment);
                log.info("Created facility payment ID: {} for provider: {} with amount: {} ({} claims)",
                        facilityPayment.getId(), facilityData.providerId, facilityData.amount, facilityData.claimIds.size());
            }
            final Long savedPaymentId = facilityPayment.getId();

            // Update cursor with payment checkpoint
            cursor.setLastProcessedPaymentId(savedPaymentId);

            // Batch update claims with payment ID for better performance
            List<InsuranceClaim> claimsToUpdate = new ArrayList<>();
            int alreadyUpdatedCount = 0;
            for (UUID claimId : facilityData.claimIds) {
                try {
                    insuranceClaimsRepository.findByClaimId(claimId).ifPresent(claims -> {
                        // findByClaimId returns List<InsuranceClaim>, get first element (claimId is unique)
                        if (!claims.isEmpty()) {
                            InsuranceClaim claim = claims.get(0);
                            // Skip if claim already has a payment ID assigned (resuming scenario)
                            if (claim.getPhcClaimPaymentsId() != null) {
                                log.debug("Claim {} already has payment ID: {}. Skipping update.",
                                        claimId, claim.getPhcClaimPaymentsId());
                                return;
                            }
                            claim.setPhcClaimPaymentsId(savedPaymentId);
                            claimsToUpdate.add(claim);
                        }
                    });
                } catch (Exception e) {
                    log.error("Error fetching claim {} for update: {}", claimId, e.getMessage());
                }
            }

            // Batch save all claims for this facility
            if (!claimsToUpdate.isEmpty()) {
                try {
                    insuranceClaimsRepository.saveAll(claimsToUpdate);
                    updatedClaims += claimsToUpdate.size();
                    log.info("Updated {} claims with facility payment ID: {}", claimsToUpdate.size(), savedPaymentId);
                } catch (Exception e) {
                    log.error("Error batch updating claims for facility {}: {}", facilityData.providerId, e.getMessage(), e);
                }
            } else {
                log.warn("No claims to update for facility: {} (expected {} claims)", facilityData.providerId, facilityData.claimIds.size());
            }
        }

        return updatedClaims;
    }
    /**
     * Calculate county-level payments using existing PHC calculation logic
     */
    private Map<String, CountyPaymentData> calculateCountyPayments(
            List<PHCClaimsDbDto> providerClaims, 
            Map<String, BigDecimal> countyPopulationMap, 
            String monthName) {

        // Reuse existing ICD code processing logic
        Map<String, List<String>> claimsIcdMap = getClaimIcdCodes(providerClaims);
        Map<String, BigDecimal> icd10Codes = new HashMap<>();
        Map<String, BigDecimal> icd11Codes = new HashMap<>();
        populateIcdCodeMaps(icd10Codes, icd11Codes);

        // Filter claims by valid ICD codes
        List<PHCClaimsDbDto> validClaims = filterClaimsByIcdCodes(providerClaims, claimsIcdMap, icd10Codes, icd11Codes);
        
        if (validClaims.isEmpty()) {
            log.error("No claims found after ICD code validation filter for month: {}", monthName);
            return new HashMap<>();
        }

        // Group claims by county
        Map<String, List<PHCClaimsDbDto>> claimsByCounty = validClaims.stream()
                .filter(claim -> claim.getCountyName() != null)
                .collect(Collectors.groupingBy(PHCClaimsDbDto::getCountyName));

        Map<String, CountyPaymentData> result = new HashMap<>();

        // Calculate for each county
        for (Map.Entry<String, List<PHCClaimsDbDto>> entry : claimsByCounty.entrySet()) {
            String countyName = entry.getKey();
            List<PHCClaimsDbDto> countyClaims = entry.getValue();

            if (!countyPopulationMap.containsKey(countyName)) {
                log.warn("No population data available for county: {}", countyName);
                continue;
            }

            BigDecimal countyPopulation = countyPopulationMap.get(countyName);
            CountyPaymentData paymentData = calculateCountyPaymentData(
                    countyClaims, countyPopulation, claimsIcdMap, icd10Codes, icd11Codes);
            
            result.put(countyName, paymentData);
        }

        return result;
    }

    /**
     * Calculate payment data for a specific county
     */
    private CountyPaymentData calculateCountyPaymentData(
            List<PHCClaimsDbDto> countyClaims,
            BigDecimal countyPopulation,
            Map<String, List<String>> claimsIcdMap,
            Map<String, BigDecimal> icd10Codes,
            Map<String, BigDecimal> icd11Codes) {

        // Group claims by provider
        Map<Long, List<PHCClaimsDbDto>> claimsByProvider = countyClaims.stream()
                .collect(Collectors.groupingBy(PHCClaimsDbDto::getProviderId));

        // Calculate claim weights
        Map<String, BigDecimal> claimWeightMap = new HashMap<>();
        for (String claimId : claimsIcdMap.keySet()) {
            List<String> icdCodes = claimsIcdMap.get(claimId);
            if (icdCodes != null) {
                BigDecimal weight = calculateClaimWeight(icdCodes, icd10Codes, icd11Codes);
                claimWeightMap.put(claimId, weight);
            }
        }

        // Calculate provider weights
        Map<Long, BigDecimal> providerWeightMap = new HashMap<>();
        BigDecimal totalCountyWeight = BigDecimal.ZERO;

        for (Map.Entry<Long, List<PHCClaimsDbDto>> entry : claimsByProvider.entrySet()) {
            Long providerId = entry.getKey();
            List<PHCClaimsDbDto> providerClaims = entry.getValue();

            BigDecimal providerWeight = providerClaims.stream()
                    .map(claim -> claimWeightMap.get(claim.getClaimId().toString()))
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            providerWeightMap.put(providerId, providerWeight);
            totalCountyWeight = totalCountyWeight.add(providerWeight);
        }

        // Calculate total county amount
        BigDecimal totalCountyAmount = phcPerHeadAmount.multiply(countyPopulation);

        // Calculate facility payments
        Map<Long, FacilityPaymentData> facilityPayments = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : providerWeightMap.entrySet()) {
            Long providerId = entry.getKey();
            BigDecimal providerWeight = entry.getValue();
            List<PHCClaimsDbDto> providerClaims = claimsByProvider.get(providerId);

            BigDecimal facilityAmount = BigDecimal.ZERO;
            if (totalCountyWeight.compareTo(BigDecimal.ZERO) > 0) {
                facilityAmount = providerWeight
                        .divide(totalCountyWeight, MathContext.DECIMAL128)
                        .multiply(totalCountyAmount)
                        .setScale(2, RoundingMode.HALF_UP);
            }

            FacilityPaymentData facilityData = new FacilityPaymentData();
            facilityData.providerId = providerId;
            facilityData.providerName = providerClaims.get(0).getProviderName();
            facilityData.fidCode = providerClaims.get(0).getFidCode();
            facilityData.amount = facilityAmount;
            facilityData.numberOfClaims = providerClaims.size();
            facilityData.claimIds = providerClaims.stream()
                    .map(PHCClaimsDbDto::getClaimId)
                    .collect(Collectors.toList());

            facilityPayments.put(providerId, facilityData);
        }

        CountyPaymentData countyData = new CountyPaymentData();
        countyData.countyName = countyClaims.get(0).getCountyName();
        countyData.totalAmount = totalCountyAmount;
        countyData.population = countyPopulation;
        countyData.totalClaimCount = countyClaims.size();
        countyData.facilityPayments = facilityPayments;

        return countyData;
    }

    /**
     * Create batch payments (county level) in phc_batch_payments table
     * Links to specific county admin level from population data
     */
    private Map<String, PhcBatchPayment> createBatchPayments(
            Map<String, CountyPaymentData> countyPaymentData, YearMonth targetMonth) {

        Map<String, PhcBatchPayment> batchPayments = new HashMap<>();
        String monthName = targetMonth.format(DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH));
        
        // Get county population data to link to specific admin levels
        List<AdminLevelPopulation> countyPopulations = adminLevelPopulationRepository.findAllByMonth(monthName);
        Map<String, AdminLevelPopulation> countyPopulationMap = countyPopulations.stream()
                .collect(Collectors.toMap(cp -> cp.getAdminLevelEntity().getName(), cp -> cp));

        for (Map.Entry<String, CountyPaymentData> entry : countyPaymentData.entrySet()) {
            String countyName = entry.getKey();
            CountyPaymentData paymentData = entry.getValue();
            
            // Get the specific county's admin level from population data
            AdminLevelPopulation countyPopulation = countyPopulationMap.get(countyName);
            if (countyPopulation == null || countyPopulation.getAdminLevelEntity() == null) {
                log.error("No admin level entity found for county: {}. Skipping batch payment creation.", countyName);
                throw new BadRequestException("No admin level entity found for county: " + countyName + ". Please ensure county population data is properly configured.");
            }

            PhcBatchPayment batchPayment = PhcBatchPayment.builder()
                    .adminLevelPopulation(countyPopulation) // Link to specific county admin level population
                    .date(targetMonth.atDay(28))  // Use 28th to match cursor batch_preparation_date
                    .amount(paymentData.totalAmount)
                    .approvedAmount(null) // Will be set during approval process
                    .paidAmount(BigDecimal.ZERO)
                    .status(PhcBatchPayment.Status.PENDING)
                    .remarks("Auto calculated")
                    .build();

            batchPayment = phcBatchPaymentRepository.save(batchPayment);
            batchPayments.put(countyName, batchPayment);

            log.info("Created batch payment for county: {} with amount: {} linked to admin level: {}", 
                    countyName, paymentData.totalAmount, countyPopulation.getAdminLevelEntity().getName());
        }

        return batchPayments;
    }

    /**
     * Create facility payments and update claims with payment IDs
     */
    @Transactional
    public void createFacilityPaymentsAndUpdateClaims(
            List<PHCClaimsDbDto> providerClaims,
            Map<String, CountyPaymentData> countyPaymentData,
            Map<String, PhcBatchPayment> batchPayments) {

        List<PhcClaimPayment> allFacilityPayments = new ArrayList<>();
        Map<UUID, Long> claimToPaymentIdMap = new HashMap<>();

        for (Map.Entry<String, CountyPaymentData> countyEntry : countyPaymentData.entrySet()) {
            String countyName = countyEntry.getKey();
            CountyPaymentData countyData = countyEntry.getValue();
            PhcBatchPayment batchPayment = batchPayments.get(countyName);

            for (FacilityPaymentData facilityData : countyData.facilityPayments.values()) {
                PhcClaimPayment facilityPayment = PhcClaimPayment.builder()
                        .aggregatedClaimId(String.valueOf(UUID.randomUUID()))
                        .providerId(facilityData.providerId)
                        .amount(facilityData.amount)
                        .numberOfClaims(Double.valueOf(facilityData.numberOfClaims))
                        .batchPreparationDate(batchPayment.getDate())
                        .paymentDate(null) // Will be set when payment is made
                        .status(ExtensionStateCodeEnum.PENDING.name())
                        .remarks("Auto calculated")
                        .phcBatchPayment(batchPayment)
                        .paidAmount(BigDecimal.ZERO)
                        .build();

                allFacilityPayments.add(facilityPayment);

                // Map claim IDs to this payment
                for (UUID claimId : facilityData.claimIds) {
                    claimToPaymentIdMap.put(claimId, null); // Will be updated after save
                }
            }
        }

        // Save facility payments
        List<PhcClaimPayment> savedPayments = phcClaimPaymentRepository.saveAll(allFacilityPayments);

        // Update claim to payment ID mapping
        int paymentIndex = 0;
        for (Map.Entry<String, CountyPaymentData> countyEntry : countyPaymentData.entrySet()) {
            CountyPaymentData countyData = countyEntry.getValue();
            
            for (FacilityPaymentData facilityData : countyData.facilityPayments.values()) {
                PhcClaimPayment savedPayment = savedPayments.get(paymentIndex++);
                
                for (UUID claimId : facilityData.claimIds) {
                    claimToPaymentIdMap.put(claimId, savedPayment.getId());
                }
            }
        }

        // Update claims table with facility payment IDs
        updateClaimsWithPaymentIds(claimToPaymentIdMap);

        log.info("Created {} facility payments and updated {} claims with payment IDs", 
                savedPayments.size(), claimToPaymentIdMap.size());
    }

    /**
     * Update claims table with facility payment IDs
     */
    private void updateClaimsWithPaymentIds(Map<UUID, Long> claimToPaymentIdMap) {
        List<InsuranceClaim> claimsToUpdate = new ArrayList<>();

        for (Map.Entry<UUID, Long> entry : claimToPaymentIdMap.entrySet()) {
            UUID claimId = entry.getKey();
            Long paymentId = entry.getValue();

            List<InsuranceClaim> claims = insuranceClaimsRepository.findByClaimId(claimId)
                    .orElse(Collections.emptyList());

            for (InsuranceClaim claim : claims) {
                claim.setPhcClaimPaymentsId(paymentId);
                claimsToUpdate.add(claim);
            }
        }

        if (!claimsToUpdate.isEmpty()) {
            insuranceClaimsRepository.saveAll(claimsToUpdate);
            log.info("Updated {} claims with PHC payment IDs", claimsToUpdate.size());
        }
    }
    /**
     * Get ICD codes for claims from PHCClaimsDbDto.icdCodes field
     * ICD codes are already populated in the claims.icd_codes column (pipe-separated)
     * 
     * For cases where a claim has mutliple ICD codes, use only the FIRST ICD code as primary diagnosis and disregard the rest.
     * This ensures claims with multiple ICD codes are not ignored but processed using the primary diagnosis.
     */
    private Map<String, List<String>> getClaimIcdCodes(List<PHCClaimsDbDto> providerClaims) {
        return providerClaims.stream()
                .filter(claim -> claim.getIcdCodes() != null && !claim.getIcdCodes().trim().isEmpty())
                .collect(Collectors.toMap(
                        claim -> claim.getClaimId().toString(),
                        claim -> {
                            String[] allIcdCodes = claim.getIcdCodes().split("\\|");
                            String primaryIcd = allIcdCodes[0].trim();
                            return Collections.singletonList(primaryIcd);
                        }
                ));
    }

    private void populateIcdCodeMaps(Map<String, BigDecimal> icd10Codes, Map<String, BigDecimal> icd11Codes) {
        icdCodeRepository.findAllOrderByIcd10DiseaseCode().forEach(icdCode -> {
            icd10Codes.put(icdCode.getIcd10DiseaseCode(), icdCode.getWeight());
            if (icdCode.getIcd11Codes() != null) {
                icd11Codes.put(icdCode.getIcd11Codes(), icdCode.getWeight());
            }
        });
    }

    private List<PHCClaimsDbDto> filterClaimsByIcdCodes(
            List<PHCClaimsDbDto> claims,
            Map<String, List<String>> claimsIcdMap,
            Map<String, BigDecimal> icd10Codes,
            Map<String, BigDecimal> icd11Codes) {

        // Partition claims into valid and invalid using streams
        Map<Boolean, List<PHCClaimsDbDto>> partitionedClaims = claims.stream()
                .collect(Collectors.partitioningBy(claim -> {
                    String claimIdStr = claim.getClaimId().toString();
                    List<String> icdCodes = claimsIcdMap.get(claimIdStr);

                    if (icdCodes == null) {
//                        log.info("ICD_FILTER | claim_id={} | county={} | icd_codes=NULL | valid=false | reason=NO_ICD_CODES",
//                                claimIdStr, claim.getCountyName());
                        return false;
                    }

                    // Find which ICD codes are valid
                    List<String> validIcdCodes = icdCodes.stream()
                            .filter(icdCode -> icd10Codes.containsKey(icdCode) || icd11Codes.containsKey(icdCode))
                            .collect(Collectors.toList());

                    boolean hasValidIcd = !validIcdCodes.isEmpty();

                    if (hasValidIcd) {
//                        log.info("ICD_FILTER | claim_id={} | county={} | icd_codes={} | valid_icd_codes={} | valid=true",
//                                claimIdStr, claim.getCountyName(), icdCodes, validIcdCodes);
                    } else {
                        log.info("ICD_FILTER | claim_id={} | county={} | icd_codes={} | valid_icd_codes=NONE | valid=false | reason=NO_MATCHING_ICD",
                                claimIdStr, claim.getCountyName(), icdCodes);
                    }

                    return hasValidIcd;
                }));

        List<PHCClaimsDbDto> validClaims = partitionedClaims.get(true);
        List<PHCClaimsDbDto> invalidClaims = partitionedClaims.get(false);

        log.info("ICD filtering results: {} valid claims, {} invalid claims", validClaims.size(), invalidClaims.size());
        if (!invalidClaims.isEmpty()) {
            Map<String, Long> invalidByCounty = invalidClaims.stream()
                    .collect(Collectors.groupingBy(PHCClaimsDbDto::getCountyName, Collectors.counting()));
            log.warn("Invalid claims by county: {}", invalidByCounty);
        }

        return validClaims;
    }

    private BigDecimal calculateClaimWeight(
            List<String> icdCodes,
            Map<String, BigDecimal> icd10Codes,
            Map<String, BigDecimal> icd11Codes) {
        
        var claimIcdCountMap = icdCodes.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return claimIcdCountMap.entrySet().stream()
                .map(entry -> {
                    var icdCode = entry.getKey();
                    var count = BigDecimal.valueOf(entry.getValue());
                    var weight = icd11Codes.getOrDefault(icdCode, icd10Codes.getOrDefault(icdCode, BigDecimal.ZERO));
                    return weight.multiply(count);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Convert Mon YYYY format to YYYYMM format for external API calls
     * Example: "May 2025" -> "202505"
     */
    private String convertReadableFormatToYearMonth(String readableMonth) {
        try {
            YearMonth ym = YearMonth.parse(readableMonth, DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH));
            return ym.format(DateTimeFormatter.ofPattern("yyyyMM"));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid month format. Expected 'MMM yyyy', got: " + readableMonth, e);
        }
    }

    /**
     * Fetch county population data from external Safaricom API and save to PostgreSQL
     * Reuses the existing logic from PHCClaimService
     */
    private List<AdminLevelPopulation> fetchAndSavePopulationDataFromApi(YearMonth targetMonth, String monthName) {
        try {
            // Convert monthName from 'MMM yyyy' format to 'YYYYMM' format for API call
            String apiMonthFormat = convertReadableFormatToYearMonth(monthName);
            log.info("Fetching county population data from external API for yearMonth: {} (converted from: {})", apiMonthFormat, monthName);
            
            // Call external Safaricom API (reusing existing service)
            List<CountyStatDto> countyStats = phcPopulationStatsService.getStats(apiMonthFormat).block();
            
            if (countyStats == null || countyStats.isEmpty()) {
                log.warn("No county statistics received from external API for month: {}", monthName);
                return Collections.emptyList();
            }
            
            log.info("Received {} county statistics from external API", countyStats.size());
            
            // Convert and save to PostgreSQL admin_level_population table
            List<AdminLevelPopulation> savedPopulations = new ArrayList<>();
            
            for (CountyStatDto countyStatDto : countyStats) {
                try {
                    // Save using CountyPopulationService which handles PostgreSQL storage
                    AdminLevelPopulation savedPopulation = countyPopulationService.saveOrUpdateCountyPopulation(
                            countyStatDto.county(), 
                            countyStatDto.registrations(), 
                            monthName
                    );
                    savedPopulations.add(savedPopulation);
                    
                } catch (Exception e) {
                    log.error("Failed to save population data for county: {} - {}", countyStatDto.county(), e.getMessage());
                }
            }
            
            log.info("Successfully saved {} county population records to PostgreSQL for month: {}", savedPopulations.size(), monthName);
            return savedPopulations;
            
        } catch (Exception e) {
            log.error("Error fetching county population data from external API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // Data classes for internal calculations
    private static class CountyPaymentData {
        String countyName;
        BigDecimal totalAmount;
        BigDecimal population;
        int totalClaimCount;
        Map<Long, FacilityPaymentData> facilityPayments;
    }

    private static class FacilityPaymentData {
        Long providerId;
        String providerName;
        String fidCode;
        BigDecimal amount;
        int numberOfClaims;
        List<UUID> claimIds;
    }
}
