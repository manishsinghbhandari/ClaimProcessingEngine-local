package io.coredge.health.claims.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.InsertOneModel;
import io.coredge.health.claims.client.ErpClient;
import io.coredge.health.claims.component.ClientsComponent;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.CountyPopulation;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.repositories.*;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.app.DataProcessingException;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import io.coredge.health.claims.models.eclaims.ERPResponseModel;
import io.coredge.health.claims.models.eclaims.InterventionCodeRequest;
import io.coredge.health.claims.models.phccalculation.*;
import io.coredge.health.claims.models.phccalculation.PHCClaimsCountyWiseProviderMonthDTO;
import io.coredge.health.claims.models.redis.ClaimICDRelation;
import io.coredge.health.claims.service.phc.CountyPopulationHelper;
import io.coredge.health.claims.service.phc.PhcPopulationStatsService;
import io.coredge.health.claims.worker.SendClaimForPaymentHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.opencsv.CSVWriter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
@Slf4j
@Service
@RequiredArgsConstructor
public class PHCClaimService {

    private final CountyPopulationService countyPopulationService;

    @Autowired
    private ClaimICDService claimICDService;

    @Autowired
    private ICDCodeRepository icdCodeRepository;

    @Autowired
    private PHCClaimRepository phcClaimRepository;

    @Autowired
    private ClientsComponent clientsComponent;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private SendClaimForPaymentHandler sendClaimForPaymentHandler;

    @Value("${erp.claim.reference.link}")
    private String erpClaimReferenceLink;

    @Autowired
    private InsuranceClaimsRepository insuranceClaimsRepository;

    @Autowired
    private PHCClaimRepositoryCountry phcClaimRepositoryCountry;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final PhcPopulationStatsService phcPopulationStatsService;
    private final CountyPopulationRepository countyPopulationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_DOC_SIZE = 15 * 1024 * 1024; // 15MB

    private static final List<String> HEADERS = List.of("Claim Id", "Claim No.", "Patient ID", "Patient No.", "Gender", "Age", "National ID","Mfl Code", "Provider Name", "County Name", "ICD Codes", "Status");

    /**
     * This method will give the population count and provider count of each county for a given month.
     * @param monthStartTime
     * @param monthEndTime
     * @return
     */
    public Optional<List<PHCClaimsCountyWiseProviderMonthDTO>> getProvidersGroupedByCounty(LocalDateTime monthStartTime, LocalDateTime monthEndTime) {
        List<Provider> providerList = insuranceClaimsRepository.findDistinctProvidersByMonth(monthStartTime, monthEndTime, ExtensionStateCodeEnum.APPROVED);
        List<String> countyNames = insuranceClaimsRepository.findDistinctCountyNames(); // Get all distinct county names

        if (countyNames.isEmpty()) {
            return Optional.empty();
        }

        // Group providers by county
        Map<String, List<PHCClaimsCountyWiseProviderMonthDTO.Providers>> providerMap = providerList.stream()
                .filter(provider -> provider.getCounty() != null)
                .collect(Collectors.groupingBy(
                        Provider::getCounty,
                        Collectors.mapping(provider -> PHCClaimsCountyWiseProviderMonthDTO.Providers.builder()
                                .providerName(provider.getName())
                                .providerId(provider.getId())
                                .build(), Collectors.toList())
                ));

        // Map counties with providers (or empty list if no providers exist)
        List<PHCClaimsCountyWiseProviderMonthDTO> result = countyNames.stream()
                .map(county -> PHCClaimsCountyWiseProviderMonthDTO.builder()
                        .countyName(county)
                        .providers(providerMap.getOrDefault(county, Collections.emptyList())) // Default to empty list if no providers exist
                        .build())
                .toList();

        return Optional.of(result);
    }

    /*
     * This method is used to get the providers with claims count for a given month
     */
    public Optional<List<PHCClaimsProviderClaimCountDTO>> getProviderWithClaimsCount(LocalDateTime startDateTime, LocalDateTime endDateTime, String countyName, String monthName) {

        List<Provider> providerList = insuranceClaimsRepository.findDistinctProvidersByMonthAndCounty(startDateTime, endDateTime, ExtensionStateCodeEnum.APPROVED, countyName);

        // Fetch provider claims
        List<PHCClaimsDbDto> providerClaims = insuranceClaimsRepository
                .findCountyWiseProviderClaimsDtos(countyName, ExtensionStateCodeEnum.APPROVED)
                .orElseGet(Collections::emptyList);

        if (providerClaims.isEmpty()) {
            log.warn("No claims found for month : {}", monthName);
            return Optional.empty();
        }

        List<PHCClaimsDbDto> dateFilteredClaims = filterClaimsByDate(providerClaims, startDateTime, endDateTime);
        log.info("Date-filtered claims count: {}", dateFilteredClaims.size());

        Optional<CountyPopulation> countyPopulation = countyPopulationService.getCountyPopulation(monthName, countyName);
        if (countyPopulation.isEmpty()) {
            log.warn("No County Population found for month: {}", monthName);
            return Optional.empty();
        }

        Map<String, BigDecimal> countyPopulationMap = countyPopulation.stream()
                .collect(Collectors.toMap(CountyPopulation::getCounty, cp -> BigDecimal.valueOf(cp.getPopulation())));

        Map<String, List<String>> claimsIcdMap = getClaimIcdCodes(providerClaims);

        Map<String, BigDecimal> icd10Codes = new HashMap<>(), icd11Codes = new HashMap<>();
        populateIcdCodeMapsBig(icd10Codes, icd11Codes);

        List<PHCClaimsDbDto> validClaims  = filterClaimsByIcdCodes(providerClaims, claimsIcdMap, icd10Codes, icd11Codes);
        if (validClaims.isEmpty()) {
            log.error("No claims found after ICD code validation filter.");
            return Optional.empty();
        }

        Map<String, List<PHCClaimsDbDto>> claimsByCounty = validClaims.stream()
                .filter(claim -> claim.getCountyName() != null)
                .collect(Collectors.groupingBy(PHCClaimsDbDto::getCountyName));

        Map<Long, BigDecimal> totalAmount = new HashMap<>();

        for (var entry : claimsByCounty.entrySet()) {
            log.info("Calculating total amount per claim for county: {}", entry.getKey());
            String cntyName = entry.getKey();

            if (!countyPopulationMap.containsKey(cntyName)) {
                log.warn("No population data available for county: {}", cntyName);
                continue;
            }

            BigDecimal countyPopulationCount = countyPopulationMap.get(cntyName);
            List<PHCClaimsDbDto> countyClaims = entry.getValue();

            Map<Long, List<String>> providerClaimsIdMap = countyClaims.stream()
                    .collect(Collectors.groupingBy(
                            PHCClaimsDbDto::getProviderId,
                            Collectors.mapping(claim -> claim.getClaimId().toString(), Collectors.toList())
                    ));

            Map<Long, BigDecimal> providerWeightMap = new HashMap<>();
            Map<String, BigDecimal> claimWeightMap = new HashMap<>(); // Store claimId -> total weight

            BigDecimal totalProvidersWeight = BigDecimal.ZERO;

            // Compute claim weights first and store in claimWeightMap
            for (String claimId : claimsIcdMap.keySet()) {
                List<String> icdCodes = claimsIcdMap.get(claimId);
                if (icdCodes != null) {
                    BigDecimal totalClaimWeight = calculateClaimWeight(icdCodes, icd10Codes, icd11Codes, new HashMap<>());
                    claimWeightMap.put(claimId, totalClaimWeight); // Store total weight per claimId
                }
            }

            // Compute provider weights based on claims they have
            for (var map : providerClaimsIdMap.entrySet()) {
                Long providerKey = map.getKey();

                BigDecimal sumWeight = map.getValue().stream()
                        .map(claimWeightMap::get) // Use precomputed claim weights
                        .filter(Objects::nonNull) // Avoid null values
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalProvidersWeight = totalProvidersWeight.add(sumWeight);
                providerWeightMap.put(providerKey, sumWeight);
            }

            for (var providerMap : providerWeightMap.entrySet()) {
                Long providerId = providerMap.getKey();
                BigDecimal providerWeight = providerMap.getValue();
                BigDecimal perHead = new BigDecimal("75.0");

                // Perform precise calculations without rounding
                BigDecimal totalAmountFactor = providerWeight
                        .divide(totalProvidersWeight, MathContext.DECIMAL128) // High precision division
                        .multiply(perHead)
                        .multiply(countyPopulationCount);

                BigDecimal roundedTotalAmount = totalAmountFactor.setScale(2, RoundingMode.HALF_UP);

                totalAmount.put(providerId, roundedTotalAmount);
                log.info("Provider ID: {}, Total Amount: {}", providerId, totalAmountFactor);
            }
        }

        return Optional.of(buildProviderClaimCountList(validClaims, totalAmount, providerList));
    }

    private List<PHCClaimsProviderClaimCountDTO> buildProviderClaimCountList(List<PHCClaimsDbDto> claims, Map<Long, BigDecimal> providerWeightMap, List<Provider> providerList) {
        // Group claims by providerId for easy lookup
        Map<Long, Long> claimsByProvider = claims.stream()
                .collect(Collectors.groupingBy(PHCClaimsDbDto::getProviderId, Collectors.counting()));

        return providerList.stream()
                .map(provider -> {
                    PHCClaimsProviderClaimCountDTO dto = new PHCClaimsProviderClaimCountDTO();
                    dto.setProviderId(provider.getId());
                    dto.setProviderName(provider.getName());
                    // Get claims for the provider, default to an empty list if none exist
                    dto.setCount(claimsByProvider.getOrDefault(provider.getId(), 0L));
                    // Set ownership if available
                    if (StringUtils.isNotEmpty(provider.getEntityType())) {
                        dto.setOwnership(getEntityType(provider.getEntityType()));
                    }
                    // Get total amount (weight) from the map, default to 0 if not present
                    dto.setTotalAmount(providerWeightMap.getOrDefault(provider.getId(), BigDecimal.valueOf(0.0)));
                    return dto;
                })
                .toList();
    }

    private String getEntityType(String ownership) {
        return switch (ownership.toLowerCase()) {
            case String s when s.contains("gok") || s.contains("ministry") -> "GoK";
            case String s when s.contains("private") -> "Private";
            case String s when s.contains("faith") || s.contains("catholic") || s.contains("christian") -> "Faith-based";
            default -> "Unknown";
        };
    }


    private PHCClaimsSummaryDTO mapToPHCClaimsSummaryDTO(PHCClaimsSummary summary) {
        return PHCClaimsSummaryDTO.builder()
                .claimNo(summary.getClaimNo())
                .claimId(summary.getClaimId())
                .nationalId(summary.getNationalId())
                .patientName(summary.getPatientName())
                .patientAge(summary.getAge())
                .patientGender(summary.getGender())
                .claimWeight(summary.getClaimWeights())
                .icdCodes(summary.getIcdCodes())
                .patientId(summary.getPatientId())
                .totalAmount(summary.getTotalAmount().setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    // TODO : Makes changes and use main logic but for one county and one provider.
    /*
     * This method is used to get the phc claims calculation summary for a given month, provider, county.
     */
    public List<PHCClaimsSummaryDTO> findClaimsSummary(String monthName, String countyName, Long providerId, Boolean sendForPayment) {
        log.info("Fetching provider claims for county: {} , providerId: {} ", countyName, providerId);
        try {
            List<PHCClaimsSummary> response = getByCountyAndProviderAndMonth(countyName, monthName, providerId);
            return response.stream().map(this::mapToPHCClaimsSummaryDTO).toList();
        } catch (Exception e) {
            log.error("Error while fetching claims summary: {} ", e.getMessage());
            throw new RuntimeException("Error while fetching claims summary : " + e.getMessage());
        }
    }

    public static int calculateAge(Date birthDate) {
        if (birthDate == null) {
            log.warn("Birth date is null, cannot calculate age.");
            return 0;
        }

        LocalDate birthLocalDate;
        try {
            //if age is invalid, return 0
            birthLocalDate = birthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (Exception e) {
            log.error("Age calculation failed: {}", e.getMessage());
            return 0;
        }

        LocalDate now = LocalDate.now();
        int age = now.getYear() - birthLocalDate.getYear();
        if (now.getMonthValue() < birthLocalDate.getMonthValue() ||
                (now.getMonthValue() == birthLocalDate.getMonthValue() && now.getDayOfMonth() < birthLocalDate.getDayOfMonth())) {
            age--;
        }

        return age;
    }

    private List<PHCClaimsDbDto> filterClaimsByDate(List<PHCClaimsDbDto> claims, LocalDateTime start, LocalDateTime end) {
        return claims.stream()
                .filter(dto -> isWithinRange(dto.getDateFrom(), start, end))
                .toList();
    }

    private boolean isWithinRange(LocalDateTime dateFrom, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (dateFrom == null || startDateTime == null || endDateTime == null) {
            return false; // Null safety check
        }
        // Normalize precision to seconds
        dateFrom = dateFrom.withNano(0);
        startDateTime = startDateTime.withNano(0);
        endDateTime = endDateTime.withNano(0);

        return !dateFrom.isBefore(startDateTime) && !dateFrom.isAfter(endDateTime);
    }

    public Map<String, Object> generateFile(Long providerId, String countyName, String monthName, String fileType, Boolean saveFlag) {

        LocalDateTime[] dateRange = Helper.getStartAndEndDateTime(Helper.decodeValue(monthName));
        LocalDateTime endDateTime = dateRange[1];

        if (endDateTime.isAfter(LocalDateTime.now()) && endDateTime.getMonth() == LocalDateTime.now().getMonth() && endDateTime.getYear() == LocalDateTime.now().getYear()) {
            log.warn("Cannot generate file before the cutoff Date of the month.");
            throw new BadRequestException("Cannot generate file before the cutoff Date of the month.");
        }

        List<PHCClaimsSummary> phcClaimsSummaryList = getByCountyAndProviderAndMonth(countyName, monthName, providerId);

        String fileName = "claims_summary_" + monthName +
                ("excel".equalsIgnoreCase(fileType) ? ".xlsx" : ".csv");

        ByteArrayInputStream stream;
        if ("csv".equalsIgnoreCase(fileType)) {
            stream = generateCsv(phcClaimsSummaryList);
        } else {
            throw new IllegalArgumentException("Invalid file type: " + fileType);
        }

        Map<String, Object> fileData = new HashMap<>();
        fileData.put("stream", stream);
        fileData.put("fileName", fileName);
        return fileData;
    }


    public static ByteArrayInputStream generateCsv(List<PHCClaimsSummary> summaryList) {
        if (summaryList.isEmpty()) {
            throw new IllegalArgumentException("The summary list is empty, cannot generate CSV.");
        }
        try (StringWriter writer = new StringWriter(); CSVWriter csvWriter = new CSVWriter(writer)) {
            // Use reflection to get headers from the DTO
            Field[] fields = PHCClaimsSummary.class.getDeclaredFields();

            // List of field names to ignore
            List<String> ignoredFields = List.of("id", "month", "totalAmount", "providerId","claimWeights");

            // Filter and collect the remaining field names for headers
            List<String> headers = Arrays.stream(fields)
                    .map(Field::getName)
                    .filter(fieldName -> !ignoredFields.contains(fieldName)) // Exclude fields
                    .map(header -> header.substring(0, 1).toUpperCase() + header.substring(1)) // Capitalize the first letter
                    .toList();

            // Write headers to CSV
            csvWriter.writeNext(headers.toArray(new String[0]));

            // Create a NumberFormat for currency (KES)
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "KE"));
            currencyFormat.setMaximumFractionDigits(2);  // Adjust if needed for rounding

            // Write data rows to CSV
            for (PHCClaimsSummary data : summaryList) {
                String[] row = Arrays.stream(fields)
                        .filter(field -> !ignoredFields.contains(field.getName())) // Exclude ignored fields
                        .map(field -> {
                            field.setAccessible(true);
                            try {
                                Object value = field.get(data);

                                // If it's the totalAmount field, format as currency
                                if ("totalAmount".equals(field.getName()) && value instanceof Double) {
                                    return currencyFormat.format(value); // Format as currency (KES)
                                } else {
                                    // Handle normal fields
                                    return value != null ? value.toString() : "";
                                }
                            } catch (IllegalAccessException e) {
                                return "";
                            }
                        })
                        .toArray(String[]::new);

                csvWriter.writeNext(row);
            }
            csvWriter.close();
            return new ByteArrayInputStream(writer.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("Error while generating CSV: {}", e.getMessage());
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    private List<PHCClaimsSummary> getByCountyAndProviderAndMonth(String county, String month, Long providerId) {
        Optional<List<PHCClaimsSummary>> response = phcClaimRepository.findByCountyNameAndMonthAndProviderId(county, month, providerId);
        if (response.isEmpty()) {
            log.warn("No claims found for the specified county: {} ", county);
            throw new RuntimeException("No claims found for the specified county: " + county);
        }
        return response.get();
    }

    private List<PHCClaimsSummary> getByMonthAndStatus(String month, ExtensionStateCodeEnum status) {
        Optional<List<PHCClaimsSummary>> response = phcClaimRepository.findByMonthAndStatus(month, status);
        if (response.isEmpty()) {
            log.warn("No claims found for the specified month: {} ", month);
            throw new RuntimeException("No claims found for the specified month: " + month);
        }
        return response.get();
    }

    private List<CSV_PHCClaimSummary> getByMonth(String month) {
        long startTime = System.currentTimeMillis();
        Optional<List<CSV_PHCClaimSummary>> response = phcClaimRepository.findByMonth(month);
        if (response.isEmpty()) {
            long endTime = System.currentTimeMillis();
            log.info("Time taken to fetch claims for the specified month: {} ms", (endTime - startTime));
            log.warn("No claims found for the specified month: {}.", month);
            throw new RuntimeException("No claims found for the specified month: " + month);
        }
        long endTime = System.currentTimeMillis();
        log.info("Time taken to fetch claims for the specified month: {} ms", (endTime - startTime));
        return response.get();
    }

    /**
     * This method is used to update the claim status in the DB.
     * @param phcClaimsSummaryList
     */
    private void updateClaimStatus(List<PHCClaimsSummary> phcClaimsSummaryList) {
        log.info("Updating PHC Claims Status to: {}", ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);

        // Extract claim IDs from PHCClaimsSummary
        List<UUID> claimIds = phcClaimsSummaryList.stream().map(PHCClaimsSummary::getClaimId)
                .filter(Objects::nonNull).map(UUID::fromString)
                .toList();

        // Fetch claims from the repository
        List<InsuranceClaim> claims = insuranceClaimsRepository.findByClaimIdIn(claimIds).orElse(Collections.emptyList());

        // Update and save InsuranceClaim statuses
        claims.forEach(claim -> claim.setStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING));
        insuranceClaimsRepository.saveAll(claims);

        // Update and save PHCClaimsSummary statuses
        phcClaimsSummaryList.forEach(phcSummary ->
                phcSummary.setStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING)
        );
        phcClaimRepository.saveAll(phcClaimsSummaryList);

        log.info("Successfully updated status to: {}", ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);
    }

    /**
     * This method is used to generate file for country wise data for a given month
     * @param countryName
     * @param monthName
     * @param fileType
     * @return
     */
    public Map<String, Object> generateFileCountryWiseData(String countryName, String monthName, String fileType) {
        log.info("Generating file for country: {} and month: {}", countryName, monthName);
        long startTime = System.currentTimeMillis();

        if (!countryName.equalsIgnoreCase("Kenya")) {
            long endTime = System.currentTimeMillis();
            log.info("Time taken for data fetching from DB and CSV generation: {} ms", (endTime - startTime));
            log.warn("Country not supported: {}", countryName);
            throw new BadRequestException("Country not supported: " + countryName);
        }

        LocalDateTime[] dateRange = Helper.getStartAndEndDateTime(Helper.decodeValue(monthName));
        LocalDateTime endDateTime = dateRange[1];

        if ((endDateTime.isAfter(LocalDateTime.now()) && endDateTime.getMonth() == LocalDateTime.now().getMonth() && endDateTime.getYear() == LocalDateTime.now().getYear()) ||  endDateTime.isAfter(LocalDateTime.now().plusMonths(1))) {
            long endTime = System.currentTimeMillis();
            log.info("Time taken for data fetching from DB and CSV generation: {} ms", (endTime - startTime));
            log.warn("Cannot generate file before the cutoff Date of the month.");
            throw new BadRequestException("Cannot generate file before the cutoff Date of the month.");
        }

        if (phcClaimRepository.existsByMonth(monthName)) {
            log.info("Fetching PHC Claims Summary from Database...");
            List<CSV_PHCClaimSummary> response = getByMonth(monthName);
            String fileName = "claims_summary_" + monthName + ".csv";

            ByteArrayInputStream stream;
            if ("csv".equalsIgnoreCase(fileType)) {
                stream = generateCsvCountryWise(response);
            } else {
                long endTime = System.currentTimeMillis();
                log.info("Time taken for data fetching from DB and CSV generation: {} ms", (endTime - startTime));
                log.warn("Invalid file type: {}", fileType);
                throw new IllegalArgumentException("Invalid file type: " + fileType);
            }

            Map<String, Object> fileData = new HashMap<>();
            fileData.put("stream", stream);
            fileData.put("fileName", fileName);
            long endTime = System.currentTimeMillis();
            log.info("Time taken for data fetching from DB and CSV generation: {} ms", (endTime - startTime));
            return fileData;
        } else {
            log.warn("No claims found for the specified month while generating report: {}.", monthName);
            long endTime = System.currentTimeMillis();
            log.info("Time taken for data fetching from DB and CSV generation: {} ms", (endTime - startTime));
            throw new DataProcessingException(String.format("We're currently processing data for %s . Please check back shortly.", monthName));
        }
    }

    public static ByteArrayInputStream generateCsvCountryWise(List<CSV_PHCClaimSummary> summaryList) {
        long startTime = System.currentTimeMillis();
        if (summaryList.isEmpty()) {
            long endTime = System.currentTimeMillis();
            log.info("Time taken to generate CSV: {} ms", (endTime - startTime));
            log.warn("The summary list is empty, cannot generate CSV.");
            throw new IllegalArgumentException("The summary list is empty, cannot generate CSV.");
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 8192);
             CSVWriter csvWriter = new CSVWriter(writer)) {

            csvWriter.writeNext(HEADERS.toArray(new String[0]));

            List<String[]> rows = summaryList.stream()
                    .map(PHCClaimService::processRow)
                    .toList();

            csvWriter.writeAll(rows);
            writer.flush();

            long endTime = System.currentTimeMillis();
            log.info("Time taken to generate CSV: {} ms", (endTime - startTime));
            return new ByteArrayInputStream(outputStream.toByteArray());

        } catch (IOException e) {
            long endTime = System.currentTimeMillis();
            log.info("Time taken to generate CSV: {} ms", (endTime - startTime));
            log.error("Error while generating CSV: {}", e.getMessage());
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    private static String[] processRow(CSV_PHCClaimSummary data) {
        return new String[]{
                StringUtils.defaultString(data.getClaimId()),
                StringUtils.defaultString(String.valueOf(data.getClaimNo())),
                StringUtils.defaultString(data.getPatientId()),
                StringUtils.defaultString(data.getPatientName()),
                StringUtils.defaultString(data.getGender()),
                String.valueOf(data.getAge()),
                StringUtils.defaultString(data.getNationalId()),
                StringUtils.defaultString(data.getMflCode()),
                StringUtils.defaultString(data.getProviderName()),
                StringUtils.defaultString(data.getCountyName()),
                StringUtils.defaultString(data.getIcdCodes()),
                StringUtils.defaultString(String.valueOf(data.getStatus()))
        };
    }

    //    @Scheduled(cron = "* 59 23 27 * ?")
    public void automatePhcClaimsCalculation() {
        log.info("Automating PHC Claims Calculation...");
        String monthName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM yyyy"));
        //        String monthName = "Jan 2025";
        findClaimsSummaryAuto(monthName);
    }

    @Async("virtualThreadExecutor")
    public void automatePhcClaimsCalculation(String monthName) {
        log.info("Started: Automating PHC Claims Calculation...");
        findClaimsSummaryAuto(monthName);
    }

//    @Scheduled(cron = "* 59 23 28 * ?")
    public void automatePhcClaimsPaymentProcessing() {
        log.info("Automating PHC Claims Payment Processing...");
//        String monthName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM"));
        String monthName = "Jan 2025";
        sendForPaymentAuto(monthName);
    }

    @Async("virtualThreadExecutor")
    public void automatePhcClaimsPaymentProcessing(String monthName) {
        log.info("Started: Automating PHC Claims Payment Processing...");
        sendForPaymentAuto(monthName);
    }

    /*
     * This method is used to get the phc claims calculation summary for a given month, provider, county.
     */
    public void findClaimsSummaryAuto(String monthName) {
        long start = System.currentTimeMillis();
        log.info("Fetching provider claims for month: {}", monthName);
        try {
            LocalDateTime[] dateRange = Helper.getStartAndEndDateTime(Helper.decodeValue(monthName));
            LocalDateTime startDateTime = dateRange[0];
            LocalDateTime endDateTime = dateRange[1];

            if (!phcClaimRepository.existsByMonthAndStatus(monthName, ExtensionStateCodeEnum.APPROVED)) {
                // EPORTAL-536 --Integrate Population Fetch API for PHC Population data
//                String yearMonth = CountyPopulationHelper.convertMonthNameToYearMonth(monthName);
//                log.info("Year Months: {}", yearMonth);
//                Mono<List<CountyStatDto>> countyDetails = phcPopulationStatsService.getStats(yearMonth);
//                countyDetails.flatMapMany(Flux::fromIterable).map(CountyPopulationHelper::mapToCountyPopulation).flatMap(entity -> Mono.fromCallable(() -> {
//                    Optional<CountyPopulation> existing = countyPopulationRepository.findAllByMonthAndCounty(entity.getCounty(), entity.getMonth());
//                    existing.ifPresent(e -> entity.setId(e.getId()));
//                    return countyPopulationRepository.save(entity);
//                }).doOnError(e -> log.error("Failed to save entity: {}", entity, e))).onErrorContinue((throwable, object) -> log.warn("Skipped saving record due to error: {}", throwable.getMessage())).collectList().doOnSuccess(saved -> log.info("Saved {} counties", saved.size())).subscribe();
                phcCalAll(startDateTime, endDateTime, monthName);
            }
            long end = System.currentTimeMillis();
            log.info("Execution time: {} ms", (end - start));
        } catch (Exception e) {
            log.error("Error while processing claims summary: {} ", e.getMessage());
            throw new RuntimeException("Failed to generate claims summary" + e.getMessage());
        }
    }

    private static final int THREAD_COUNT = 2; // 2 Threads for ERP calls
    private static final int DB_BATCH_SIZE = 1000; // Batch size for DB updates
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);


    /**
     * Sends claims for payment to ERP using 2 threads.
     * @param monthName
     */
    public void sendForPaymentAuto(String monthName) {
        log.info("Initiating PHC Claims payment process for month: {}", monthName);

        try {
            List<PHCClaimsSummary> claims = getByMonthAndStatus(monthName, ExtensionStateCodeEnum.APPROVED);
            if (claims.isEmpty()) {
                log.warn("No PHC Claims found for payment in month: {}", monthName);
                return;
            }

            var providerClaimsMap = claims.stream().collect(Collectors.groupingBy(PHCClaimsSummary::getProviderId));

            List<Future<Void>> futures = new ArrayList<>();

            for (var entry : providerClaimsMap.entrySet()) {
                futures.add(executorService.submit(() -> processProviderClaims(entry.getValue())));
            }

            for (Future<Void> future : futures) {
                future.get(); // Ensure all threads complete before exiting
            }

            log.info("All PHC claims processing completed for month: {}", monthName);

        } catch (Exception e) {
            log.error("Unexpected error while processing PHC Claims for month {}: {}", monthName, e.getMessage());
            throw new RuntimeException("Failed to send claims for payment: " + e.getMessage());
        }
    }

    /**
     * Processes claims for a single provider.
     */
    private Void processProviderClaims(List<PHCClaimsSummary> providerClaims) {
        try {
            PHCClaimsSummary summary = providerClaims.getFirst();
            log.info("Processing payment for Provider: {}/{}", summary.getProviderName(), summary.getMflCode());

            List<String> claimIds = providerClaims.stream()
                    .map(PHCClaimsSummary::getClaimId)
                    .toList();

            List<String> serviceCodes = getClaimLineItems(claimIds);

            if (serviceCodes.isEmpty()) {
                log.warn("No line items found for provider: {}/{}", summary.getProviderName(), summary.getMflCode());
                return null;
            }

            CreateClaimFundScheme claimRequest = createClaimFundScheme(summary, serviceCodes);

            String requestBodyJson = new Gson().toJson(claimRequest);
            log.info("ERP Request JSON for Provider {}/{}: {}", summary.getProviderName(), summary.getMflCode(), requestBodyJson);

            String erpResponse = clientsComponent.processERPClaim(claimRequest);

            if (erpResponse.contains("201") && !erpResponse.contains("Error:")) {
                ERPResponseModel erpResponseModel = objectMapper.readValue(erpResponse, ERPResponseModel.class);
                log.info("Payment successfully sent to ERP for Provider: {}/{}", summary.getProviderName(), summary.getMflCode());

                sendClaimForPaymentHandler.saveToErpClaims(false, claimRequest, erpResponseModel, null);

                // Update claim statuses asynchronously
                updateClaimStatusAsync(providerClaims);
            } else {
                log.warn("ERP Response Error for Provider {}/{}: {}", summary.getProviderName(), summary.getMflCode(), erpResponse);
            }
        } catch (Exception e) {
            log.error("Error processing provider claims: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Updates the claim status asynchronously using batch processing.
     */
    @Async("virtualThreadExecutor")
    public void updateClaimStatusAsync(List<PHCClaimsSummary> phcClaimsSummaryList) {
        try {
            log.info("Updating PHC Claims Status to: {}", ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);

            // Extract claim IDs
            List<UUID> claimIds = phcClaimsSummaryList.stream()
                    .map(PHCClaimsSummary::getClaimId)
                    .filter(Objects::nonNull)
                    .map(UUID::fromString)
                    .toList();

            // Fetch claims in batches
            List<InsuranceClaim> claims = insuranceClaimsRepository.findByClaimIdIn(claimIds)
                    .orElse(Collections.emptyList());

            claims.forEach(claim -> claim.setStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING));

            // Save InsuranceClaims in batches
            batchSaveInsuranceClaims(claims);

            phcClaimsSummaryList.forEach(phcSummary ->
                    phcSummary.setStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING)
            );

            // Save PHCClaimsSummary in batches
            batchSavePHCClaims(phcClaimsSummaryList);

            log.info("Successfully updated status to: {}", ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);
        } catch (Exception e) {
            log.error("Error updating claim status: {}", e.getMessage());
        }
        CompletableFuture.completedFuture(null);
    }

    /**
     * Batch saves InsuranceClaims.
     */
    private void batchSaveInsuranceClaims(List<InsuranceClaim> claims) {
        for (int i = 0; i < claims.size(); i += DB_BATCH_SIZE) {
            int end = Math.min(i + DB_BATCH_SIZE, claims.size());
            List<InsuranceClaim> batch = claims.subList(i, end);
            insuranceClaimsRepository.saveAll(batch);
        }
    }

    /**
     * Batch saves PHCClaimsSummary.
     */
    private void batchSavePHCClaims(List<PHCClaimsSummary> claims) {
        for (int i = 0; i < claims.size(); i += DB_BATCH_SIZE) {
            int end = Math.min(i + DB_BATCH_SIZE, claims.size());
            List<PHCClaimsSummary> batch = claims.subList(i, end);
            phcClaimRepository.saveAll(batch);
        }
    }

    private CreateClaimFundScheme createClaimFundScheme(PHCClaimsSummary summary, List<String> serviceCodes) {

        Optional<List<ClaimLineItem>> lineItemsOpt =
                lineItemRepository.findByClaimId(UUID.fromString(summary.getClaimId()));

        List<InterventionCodeRequest> interventionCodes = new ArrayList<>();

        if (lineItemsOpt.isPresent() && !lineItemsOpt.get().isEmpty()) {
            List<ClaimLineItem> items = lineItemsOpt.get();
            // Stream mapping to InterventionCodeRequest
            items.forEach(item -> {
                InterventionCodeRequest request = new InterventionCodeRequest();
                request.setServiceCodeType(item.getCode());
                request.setAmount(item.getApprovedPrice());
                interventionCodes.add(request);
            });
            log.info("Total intervention codes created: {}", interventionCodes.size());
        } else {
            log.warn("No line items found for claimId: {}", summary.getClaimId());
        }


        List<CreateClaimFundScheme.FundScheme> fundSchemes = List.of(
                CreateClaimFundScheme.FundScheme.builder()
                        .fundSchemeType("PHC")
                        .amount(summary.getTotalAmount())
                        .build()
        );
        log.info("PHC Date : ", Helper.getLastDateTimeOfMonth(summary.getMonth()));
        return CreateClaimFundScheme.builder()
                .claimReferenceLink(erpClaimReferenceLink)
                .vendorCode(summary.getMflCode().replaceFirst("^MFL-", ""))
                .vendorName(summary.getProviderName())
                .beneficiaryName("SHA")
                .beneficiaryShaNo("SHA")
                .claimAmount(summary.getTotalAmount())
                .claimNo(String.valueOf(summary.getClaimNo()))
                .invoiceNumber(String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999)))
                .serviceCode(serviceCodes)
                .fundScheme(fundSchemes)
                .serviceEndDate(Helper.getServiceDate(Helper.getLastDateTimeOfMonth(summary.getMonth())))
                .interventionCodes(interventionCodes)
                .build();
    }

    /**
     * This method is used to get the phc claims calculation summary for a given month.
     * @param startDateTime
     * @param endDateTime
     * @param monthName
     */
    @Async("virtualThreadExecutor")
    public void phcCalAll(LocalDateTime startDateTime, LocalDateTime endDateTime, String monthName) {

        List<PHCClaimsDbDto> providerClaims = insuranceClaimsRepository
                .findPhcClaimsDtosDatesFilter(ExtensionStateCodeEnum.APPROVED, startDateTime, endDateTime)
                .orElseGet(Collections::emptyList);

        if (providerClaims.isEmpty()) {
            log.warn("No claims found for month : {}", monthName);
            return;
        } else {
            log.info("Total claims found: {}", providerClaims.size());
        }

        List<CountyPopulation> countyPopulation = countyPopulationService.getCountyPopulationsByMonth(monthName);
        if (countyPopulation.isEmpty()) {
            log.warn("No County Population found for month: {}", monthName);
            return;
        }

        Map<String, BigDecimal> countyPopulationMap = countyPopulation.stream()
                .collect(Collectors.toMap(CountyPopulation::getCounty, cp -> BigDecimal.valueOf(cp.getPopulation())));

        calculationMethod(providerClaims, countyPopulationMap, monthName);
    }

    /**
     * This method is used to get the phc claims calculation summary for a given month and county.
     * @param startDateTime
     * @param endDateTime
     * @param countyName
     * @param monthName
     */
    public void phcCalPerCounty(LocalDateTime startDateTime, LocalDateTime endDateTime, String countyName, String monthName, Long providerId) {

        // Fetch provider claims
        List<PHCClaimsDbDto> providerClaims = insuranceClaimsRepository
                .findCountyWiseProviderClaimsDtos(countyName, ExtensionStateCodeEnum.APPROVED)
                .orElseGet(Collections::emptyList);

        if (providerClaims.isEmpty()) {
            log.warn("No claims found for month : {}", monthName);
            return;
        }

        List<PHCClaimsDbDto> dateFilteredClaims = filterClaimsByDate(providerClaims, startDateTime, endDateTime);
        log.info("Date-filtered claims count: {}", dateFilteredClaims.size());

        Optional<CountyPopulation> countyPopulation = countyPopulationService.getCountyPopulation(monthName, countyName);
        if (countyPopulation.isEmpty()) {
            log.warn("No County Population found for month: {}", monthName);
            return;
        }

        Map<String, BigDecimal> countyPopulationMap = countyPopulation.stream()
                .collect(Collectors.toMap(CountyPopulation::getCounty, cp -> BigDecimal.valueOf(cp.getPopulation())));

        calculationMethod(dateFilteredClaims, countyPopulationMap, monthName);
    }

    /**
     * Calculate PHC Claims Summary per county.
     * @param providerClaims
     * @param countyPopulationMap
     * @param monthName
     */
    public void calculationMethod(List<PHCClaimsDbDto> providerClaims, Map<String, BigDecimal> countyPopulationMap, String monthName){

        Map<String, List<String>> claimsIcdMap = getClaimIcdCodes(providerClaims);

        Map<String, BigDecimal> icd10Codes = new HashMap<>(), icd11Codes = new HashMap<>();
        populateIcdCodeMapsBig(icd10Codes, icd11Codes);

        List<PHCClaimsDbDto> validClaims  = filterClaimsByIcdCodes(providerClaims, claimsIcdMap, icd10Codes, icd11Codes);
        if (validClaims.isEmpty()) {
            log.error("No claims found after ICD code validation filter.");
            return;
        }

        Map<String, List<PHCClaimsDbDto>> claimsByCounty = validClaims.stream()
                .filter(claim -> claim.getCountyName() != null)
                .collect(Collectors.groupingBy(PHCClaimsDbDto::getCountyName));

        List<PHCClaimsSummary> summary = calculationPerCounty(claimsByCounty, countyPopulationMap, claimsIcdMap, icd10Codes, icd11Codes, monthName);
        if(!summary.isEmpty())
            savePhcClaimSummaryList(summary);

    }

    /**
     * Calculate PHC Claims Summary per county.
     * @param claimsByCounty
     * @param countyPopulationMap
     * @param claimsIcdMap
     * @param icd10Codes
     * @param icd11Codes
     * @param monthName
     * @return
     */
    private List<PHCClaimsSummary> calculationPerCounty(Map<String, List<PHCClaimsDbDto>> claimsByCounty,
                                                        Map<String, BigDecimal> countyPopulationMap,
                                                        Map<String, List<String>> claimsIcdMap,
                                                        Map<String, BigDecimal> icd10Codes,
                                                        Map<String, BigDecimal> icd11Codes,
                                                        String monthName) {

        List<PHCClaimsSummary> summaryList = new ArrayList<>();
        for (var entry : claimsByCounty.entrySet()) {
            log.info("Calculating total amount per claim for county: {}", entry.getKey());
            String countyName = entry.getKey();

            if (!countyPopulationMap.containsKey(countyName)) {
                log.warn("No population data available for county: {}", countyName);
                continue;
            }

            BigDecimal countyPopulationCount = countyPopulationMap.get(countyName);
            List<PHCClaimsDbDto> countyClaims = entry.getValue();

            Map<Long, List<String>> providerClaimsIdMap = countyClaims.stream()
                    .collect(Collectors.groupingBy(
                            PHCClaimsDbDto::getProviderId,
                            Collectors.mapping(claim -> claim.getClaimId().toString(), Collectors.toList())
                    ));

            Map<Long, BigDecimal> providerWeightMap = new HashMap<>();
            Map<String, BigDecimal> claimWeightMap = new HashMap<>(); // Store claimId -> total weight

            BigDecimal totalProvidersWeight = BigDecimal.ZERO;

            // Compute claim weights first and store in claimWeightMap
            for (String claimId : claimsIcdMap.keySet()) {
                List<String> icdCodes = claimsIcdMap.get(claimId);
                if (icdCodes != null) {
                    BigDecimal totalClaimWeight = calculateClaimWeight(icdCodes, icd10Codes, icd11Codes, new HashMap<>());
                    claimWeightMap.put(claimId, totalClaimWeight); // Store total weight per claimId
                }
            }

            // Compute provider weights based on claims they have
            for (var map : providerClaimsIdMap.entrySet()) {
                Long providerKey = map.getKey();

                BigDecimal sumWeight = map.getValue().stream()
                        .map(claimWeightMap::get) // Use precomputed claim weights
                        .filter(Objects::nonNull) // Avoid null values
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalProvidersWeight = totalProvidersWeight.add(sumWeight);
                providerWeightMap.put(providerKey, sumWeight);
            }

            for (var providerMap : providerClaimsIdMap.entrySet()) {
                Long providerId = providerMap.getKey();
                log.info("Calculating total amount per provider : {}", providerId);

                BigDecimal providerWeight = providerWeightMap.get(providerId);
                log.info("Calculated ICD weight :{} for Provider : {}", providerWeight, providerId);

                List<PHCClaimsSummary> list = calculationPerProvider(countyClaims, providerWeight, totalProvidersWeight,
                        providerId, monthName, claimsIcdMap,
                        claimWeightMap, countyPopulationCount);
                summaryList.addAll(list);
            }
        }
        return summaryList;
    }

    /**
     * Calculate PHC Claims Summary per provider.
     * @param countyClaims
     * @param providerWeight
     * @param totalProvidersWeight
     * @param providerId
     * @param monthName
     * @param claimsIcdMap
     * @param claimWeightMap
     * @param countyPopulation
     * @return
     */
    private List<PHCClaimsSummary> calculationPerProvider(List<PHCClaimsDbDto> countyClaims, BigDecimal providerWeight, BigDecimal totalProvidersWeight,
                                                          Long providerId, String monthName, Map<String, List<String>> claimsIcdMap,
                                                          Map<String, BigDecimal> claimWeightMap, BigDecimal countyPopulation) {

        BigDecimal perHead = new BigDecimal("75.0");
        // Perform precise calculations without rounding
        BigDecimal totalAmountFactor = providerWeight
                .divide(totalProvidersWeight, MathContext.DECIMAL128) // High precision division
                .multiply(perHead)
                .multiply(countyPopulation);
        log.info("Total Amount Calculation :{} for Provider : {}", totalAmountFactor, providerId);

        BigDecimal roundedTotalAmount = totalAmountFactor.setScale(2, RoundingMode.HALF_UP);

        return mapData(countyClaims, providerId, monthName, claimsIcdMap, claimWeightMap, roundedTotalAmount);
    }

    /**
     * Calculate PHC Claims Summary per provider.
     *
     * @param countyClaims
     * @param providerWeight
     * @param totalProvidersWeight
     * @param providerId
     * @param monthName
     * @param claimsIcdMap
     * @param claimWeightMap
     * @param countyPopulation
     * @return
     */
    private Map<Long, BigDecimal> calculationPerProviderMAP(List<PHCClaimsDbDto> countyClaims, BigDecimal providerWeight, BigDecimal totalProvidersWeight,
                                                         Long providerId, String monthName, Map<String, List<String>> claimsIcdMap,
                                                         Map<String, BigDecimal> claimWeightMap, BigDecimal countyPopulation) {

        BigDecimal perHead = new BigDecimal("75.0");
        // Perform precise calculations without rounding
        BigDecimal totalAmountFactor = providerWeight
                .divide(totalProvidersWeight, MathContext.DECIMAL128) // High precision division
                .multiply(perHead)
                .multiply(countyPopulation);
        log.info("Total Amount Calculation :{} for Provider : {}", totalAmountFactor, providerId);

        BigDecimal roundedTotalAmount = totalAmountFactor.setScale(2, RoundingMode.HALF_UP);

        Map<Long, BigDecimal> providerTotalAmountMap = new HashMap<>();
        providerTotalAmountMap.put(providerId, roundedTotalAmount);
        return providerTotalAmountMap;
    }

    /**
     * Map data to PHCClaimsSummary object.
     * @param countyClaims
     * @param providerId
     * @param monthName
     * @param claimsIcdMap
     * @param claimWeightMap
     * @param roundedTotalAmount
     * @return
     */
    private List<PHCClaimsSummary> mapData(List<PHCClaimsDbDto> countyClaims, Long providerId, String monthName,
                                           Map<String, List<String>> claimsIcdMap, Map<String, BigDecimal> claimWeightMap,
                                           BigDecimal roundedTotalAmount) {
        return countyClaims.stream()
                .filter(claim -> claim.getProviderId().equals(providerId))
                .map(claim -> {
                    PHCClaimsSummary summary = new PHCClaimsSummary();
                    summary.setClaimId(claim.getClaimId().toString());
                    summary.setClaimNo(claim.getClaimNo());
                    summary.setProviderId(claim.getProviderId());
                    summary.setProviderName(claim.getProviderName());
                    summary.setMflCode(claim.getFidCode() != null ? claim.getFidCode() : claim.getMflCode());
                    summary.setCountyName(claim.getCountyName());
                    summary.setPatientId(claim.getPatientId());
                    summary.setPatientName(claim.getPatientName());
                    summary.setNationalId(claim.getNationalId());
                    summary.setGender(claim.getGender());
                    summary.setAge(calculateAge(claim.getDateOfBirth()));
                    summary.setMonth(monthName);
                    summary.setIcdCodes(getIcdCode(claimsIcdMap.get(claim.getClaimId().toString())));
                    summary.setClaimWeights(claimWeightMap.get(claim.getClaimId().toString()));
                    summary.setTotalAmount(roundedTotalAmount);
                    summary.setStatus(ExtensionStateCodeEnum.APPROVED);
                    return summary;
                })
                .toList();
    }

    /**
     * Get ICD codes for each claim from list of claims.
     * @param providerClaims
     * @return
     */
    public Map<String, List<String>> getClaimIcdCodes(List<PHCClaimsDbDto> providerClaims) {

        // Create a map of claimId -> List of ICD codes from providerClaims
        Map<String, List<String>> icdClaimMap = providerClaims.stream()
                .filter(claim -> StringUtils.isNotEmpty(claim.getIcdCodes()))
                .collect(Collectors.toMap(i -> i.getClaimId().toString(),
                        claim -> Arrays.asList(claim.getIcdCodes().split("\\|"))));

        // Get missing claim IDs (i.e., claim IDs not present in icdClaimMap)
        List<String> missingClaimIds = providerClaims.stream()
                .map(PHCClaimsDbDto::getClaimId)
                .map(UUID::toString)
                .filter(id -> !icdClaimMap.containsKey(id))
                .toList();

        if (!missingClaimIds.isEmpty()) {
            // Fetch new claim ICD mappings for missing claim IDs
            Map<String, List<String>> claimsIcdMapNew = buildClaimsIcdCodes(missingClaimIds);

            // Merge maps, preferring values from claimsIcdMapNew if a key is present in both
            return new HashMap<>(icdClaimMap) {{
                putAll(claimsIcdMapNew);
            }};
        }
        return icdClaimMap;
    }

    /**
     * Build a map of claimId -> List of ICD codes for the given claim IDs.
     * @param claimIds
     * @return
     */
    private Map<String, List<String>> buildClaimsIcdCodes(List<String> claimIds) {
        return claimICDService.getClaimsIcdByClaimIds(claimIds).stream()
                .filter(i -> Objects.nonNull(i.getDiagnosis()) && !i.getDiagnosis().isEmpty())
                .collect(Collectors.toMap(
                        ClaimICDRelation::getClaimId,
                        i -> i.getDiagnosis().stream()
                                .map(ClaimICDRelation.Diagnosis::getIcdCode)
                                .toList(),
                        (existing, replacement) -> existing // Keep existing if duplicate keys
                ));
    }

    /**
     * Populate ICD code maps with ICD10 and ICD11 codes.
     * @param icd10Codes
     * @param icd11Codes
     */
    private void populateIcdCodeMapsBig(Map<String, BigDecimal> icd10Codes, Map<String, BigDecimal> icd11Codes) {
        icdCodeRepository.findAll().forEach(icdCode -> {
            icd10Codes.put(icdCode.getIcd10DiseaseCode(), BigDecimal.valueOf(icdCode.getWeight()));
            icd11Codes.put(icdCode.getIcd11Codes(), BigDecimal.valueOf(icdCode.getWeight()));
        });
    }

    /**
     * Filter claims by only valid PHC ICD codes .
     * @param claims
     * @param claimsIcdMapNew
     * @param icd10Codes
     * @param icd11Codes
     * @return
     */
    private List<PHCClaimsDbDto> filterClaimsByIcdCodes(List<PHCClaimsDbDto> claims,
                                                        Map<String, List<String>> claimsIcdMapNew,
                                                        Map<String, BigDecimal> icd10Codes,
                                                        Map<String, BigDecimal> icd11Codes) {
        return claims.stream()
                .filter(claim -> {
                    List<String> icdCodes = claimsIcdMapNew.get(claim.getClaimId().toString());
                    return icdCodes != null && icdCodes.stream()
                            .anyMatch(icdCode -> icd10Codes.containsKey(icdCode) || icd11Codes.containsKey(icdCode));
                })
                .toList();
    }


    /**
     * Calculate the weight of a claim based on its ICD codes.
     * @param icdCodes
     * @param icd10Codes
     * @param icd11Codes
     * @param claimWeightMap
     * @return
     */
    private BigDecimal calculateClaimWeight(List<String> icdCodes, Map<String, BigDecimal> icd10Codes,
                                            Map<String, BigDecimal> icd11Codes, Map<String, BigDecimal> claimWeightMap)
    {
        var claimIcdCountMap = icdCodes.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return claimIcdCountMap.entrySet().stream()
                .map(entry -> {
                    var icdCode = entry.getKey();
                    var count = BigDecimal.valueOf(entry.getValue());

                    // Get the weight from icd11Codes or fallback to icd10Codes
                    var weight = icd11Codes.getOrDefault(icdCode, icd10Codes.getOrDefault(icdCode, BigDecimal.ZERO));
                    return weight.multiply(count); // Multiply weight by count
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add); // Sum up all weights
    }

    /**
     * This method saves the PHC Claims Summary data to the database in batches.
     * @param claimsList
     */
    private void savePhcClaimSummaryList(List<PHCClaimsSummary> claimsList) {
        log.info("Saving PHC Claims Summary Data. Total size: {}", claimsList.size());

        MongoCollection<Document> collection = mongoTemplate.getCollection("PHC_Claims_Summary");
        List<InsertOneModel<Document>> batch = new ArrayList<>();
        int batchSize = 0;

        for (PHCClaimsSummary claim : claimsList) {
            try {
                Document doc = Document.parse(objectMapper.writeValueAsString(claim));
                int docSize = doc.toJson().getBytes().length;

                if (batchSize + docSize > MAX_DOC_SIZE) {
                    collection.bulkWrite(batch);
                    batch.clear();
                    batchSize = 0;
                }

                batch.add(new InsertOneModel<>(doc));
                batchSize += docSize;
            } catch (Exception ignored) {
            }
        }

        if (!batch.isEmpty())
            collection.bulkWrite(batch);

        log.info("✅ All records saved successfully!");
    }

    private String getIcdCode(List<String> claimICDDiagnosis) {
        return claimICDDiagnosis.stream()
                .filter(Objects::nonNull) // Ensure no null values
                .collect(Collectors.joining("| ")); // Join with "| "
    }

    /**
     * This method is used to sava data after calculation of the phc claims calculation
     * for all the claims of a given month.
     * @param monthName
     */
    @Async("virtualThreadExecutor")
    public void checkAndRunAsyncMethod(String monthName) {

        LocalDateTime now = LocalDateTime.now();
        String currentMonth = now.format(DateTimeFormatter.ofPattern("MMM yyyy"));

        // If this is for current momth we will ignore this.
        if (!currentMonth.equalsIgnoreCase(monthName)) {
            // Run the async method if the month matches the current month
            LocalDateTime[] dateTime = Helper.getStartAndEndDateTime(Helper.decodeValue(monthName));
            LocalDateTime startDateTime = dateTime[0];
            LocalDateTime endDateTime = dateTime[1];

            // If already exist does not run the logic again.
            if (!phcClaimRepository.existsByMonthAndStatus(monthName, ExtensionStateCodeEnum.APPROVED)) {
                phcCalAll(startDateTime, endDateTime, monthName);
            } else {
                log.info("Data already exist for the month: {}", monthName);
            }
        } else {
            // Skip if the month does not match the current month
            log.info("Skipping async method execution as the month matches current month.");
        }
    }

    public List<String> getClaimLineItems(List<String> claimIds) {
        return lineItemRepository.findOnlyDistinctCodesByClaimIds(claimIds);
    }

}

