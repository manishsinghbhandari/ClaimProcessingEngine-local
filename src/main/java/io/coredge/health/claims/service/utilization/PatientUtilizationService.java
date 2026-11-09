package io.coredge.health.claims.service.utilization;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.utilization.BenefitsMasterView;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.db.entity.utilization.FundUtilizationLimit;
import io.coredge.health.claims.db.entity.utilization.PatientUtilization;
import io.coredge.health.claims.db.entity.utilization.UtilizationPeriod;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.MemberRepository;
import io.coredge.health.claims.db.repositories.utilization.ClaimUtilizationProjection;
import io.coredge.health.claims.db.repositories.utilization.PatientUtilizationRepository;
import io.coredge.health.claims.db.repositories.utilization.PreAuthUtilizationProjection;
import io.coredge.health.claims.dto.eligibility.CoveragePeriodDto;
import io.coredge.health.claims.dto.utilization.PatientUtilizationItemDto;
import io.coredge.health.claims.dto.utilization.PatientUtilizationResponse;
import io.coredge.health.claims.dto.utilization.PatientUtilizationResponseDto;
import io.coredge.health.claims.enums.FundType;
import io.coredge.health.claims.enums.LimitScope;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.exception.app.NoLimitException;
import io.coredge.health.claims.exception.app.PatientNotFoundException;
import io.coredge.health.claims.helpers.FinsprintHelper;
import io.coredge.health.claims.helpers.HieHelper;
import io.coredge.health.claims.models.DropDown;
import io.coredge.health.claims.models.InterventionUtilizationLimit;
import io.coredge.health.claims.models.PatientLimit;
import io.coredge.health.claims.models.intervention.dto.ClaimUsage;
import io.coredge.health.claims.models.intervention.dto.InterventionRules;
import io.coredge.health.claims.service.DMNService;
import io.coredge.health.claims.service.MemberService;
import io.coredge.health.claims.service.intervention.InterventionValidationService;
import io.coredge.health.claims.utils.PreAuthLimits;
import io.coredge.health.claims.worker.ClaimsHandlerService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.APPROVED_EXTENSION_STATES;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;
import static io.coredge.health.claims.constants.intervention.InterventionConstants.*;
import static io.coredge.health.claims.enums.FundType.PMSF;

@Transactional

@Service
@Slf4j
public class PatientUtilizationService {

    @Value("${SHA_06_ECCIF_Limit}")
    private BigDecimal eccifFundLimit;

    private final PatientUtilizationRepository patientUtilizationRepository;
    private final MemberRepository memberRepository;
    private final BenefitsMasterRepository benefitsMasterRepository;
    private final DMNService dmnService;
    private final FinsprintHelper finsprintHelper;
    private final HieHelper hieHelper;
    private final IGenericClient fhirClient;
    private final FhirContext fhirContext;
    private final MemberService memberService;
    private final ClaimsHandlerService claimsHandlerService;
    private final InterventionValidationService interventionValidationService;
    private final PreAuthLimits preAuthLimits;
    private final InsuranceClaimsRepository claimsRepository;

    public static final String COMMA = ",";
    public static final List<String> UNLIMITED_PACKAGES = List.of("sha-12","sha-01") ;
    public static final List<String> SKIP_BENEFITS = List.of("pmf");
    public static final List<String> UNALLOWED_CODES = List.of("SHA-19");
    public static final Integer MAX_PER_DIEM = 180;

    private Map<String , Map<String , BenefitsMasterView>> benefitMap;
    private Map<String , InterventionUtilizationLimit> limitsMap;

    public PatientUtilizationService(PatientUtilizationRepository patientUtilizationRepository, MemberRepository memberRepository, BenefitsMasterRepository benefitsMasterRepository, DMNService dmnService, FinsprintHelper finsprintHelper, HieHelper hieHelper, IGenericClient fhirClient, FhirContext fhirContext, MemberService memberService, ClaimsHandlerService claimsHandlerService, InterventionValidationService interventionValidationService, PreAuthLimits preAuthLimits, InsuranceClaimsRepository claimsRepository) {
        this.patientUtilizationRepository = patientUtilizationRepository;
        this.memberRepository = memberRepository;
        this.benefitsMasterRepository = benefitsMasterRepository;
        this.dmnService = dmnService;
        this.finsprintHelper = finsprintHelper;
        this.hieHelper = hieHelper;
        this.fhirClient = fhirClient;
        this.fhirContext = fhirContext;
        this.memberService = memberService;
        this.claimsHandlerService = claimsHandlerService;
        this.interventionValidationService = interventionValidationService;
        this.preAuthLimits = preAuthLimits;
        this.claimsRepository = claimsRepository;
        benefitMap = new HashMap<>();
        limitsMap = new HashMap<>();
    }

    @Scheduled(fixedRateString = "${patientUtilization.refresh.benefits.fixedRate}" , timeUnit = TimeUnit.HOURS)
    public void refreshCache() {
        refreshBenefits();
    }

    //TODO : we are not saving Utilization details in database
//    @Scheduled(cron = "${patientUtilization.refresh.benefits.cron}")
//    public void refreshAvailability(){
//        patientUtilizationRepository.deleteExpiredFundUtilizationLimits();
//        patientUtilizationRepository.deleteExpiredPatientUtilization();
//    }

    public List<PatientUtilizationResponse> getUtilizationBalance(String crId, String code) {

        if(SKIP_BENEFITS.stream().anyMatch(sub -> StringUtils.contains(code.toLowerCase() , sub.toLowerCase()))) {
            throw new BadRequestException("This Package/Sub-Package Code Utilization not Present:"+ code);
        }

        if(UNALLOWED_CODES.stream().anyMatch(sub -> StringUtils.equalsIgnoreCase(sub , code))) {
            throw new BadRequestException("This Package/Sub-Package Code is not allowed:"+ code);
        }

        if(benefitMap.containsKey(code)){
            return providePackageRecord(crId, code);
        }
        else if(benefitMap.values().stream().map(Map::values).flatMap(Collection::stream).anyMatch(balance -> balance.getInterventionCode().equalsIgnoreCase(code))){
            return provideIndividualRecord(crId, code);
        }
        else {
            throw new BadRequestException("Benefit is not found in records :"+ code);
        }
    }

    public void refreshBenefits(){
        log.info("Refreshing Benefits Cache");
        benefitMap.clear();
        List<BenefitsMasterView> records = benefitsMasterRepository.findBenefitMasterRecords();

        for (BenefitsMasterView benefit : records) {
            benefitMap
                    .computeIfAbsent(benefit.getCode(), k -> new HashMap<>())
                    .put(benefit.getInterventionCode(), benefit);
        }
        log.info("Benefits Cache refreshed");
    }

    @PostConstruct
    private void refreshLimits(){
        log.info("Refreshing Limit Cache");
        limitsMap.clear();
        Map<String, List<Map<String, String>>> decisionsRules = dmnService.getClaimDecisionRules();

        for(Map.Entry<String, List<Map<String, String>>> rule : decisionsRules.entrySet()){
            Map<String, String> filteredData = rule.getValue().stream().map(Map::entrySet)
                    .flatMap(Collection::stream)
                    .filter(entry -> (VAR_INTERVENTION_USE_CNT).equals(entry.getKey()) && !entry.getValue().isBlank()
                            || (VAR_INDIVIDUAL_USAGE).equals(entry.getKey()) && !entry.getValue().isBlank()
                            || (VAR_HOUSE_HOLD_USE_CNT).equals(entry.getKey()) && !entry.getValue().isBlank())
                    .collect(Collectors.toMap(Map.Entry::getKey , Map.Entry::getValue , (oldVal, newVal) -> newVal));

            Integer individualUsage = extractNumber(filteredData.get(VAR_INDIVIDUAL_USAGE));
            Integer householdUsage = extractNumber(filteredData.get(VAR_HOUSE_HOLD_USE_CNT));
            Integer interventionUsageCount = extractNumber(filteredData.get(VAR_INTERVENTION_USE_CNT));

            InterventionUtilizationLimit utilizationLimit = InterventionUtilizationLimit.builder()
                    .code(rule.getKey())
                    .householdUtilizationCount( householdUsage )
                    .individualUtilizationCount( interventionUsageCount  )
                    .utilizationLimit(individualUsage != null ? new BigDecimal(individualUsage) : null)
                    .build();

            limitsMap.put(rule.getKey() , utilizationLimit);
        }
        log.info("Limit Cache refreshed");
    }

    private List<PatientUtilizationResponse> providePackageRecord(String crId, String code){
        //TODO : we are not saving Utilization details in database
        //List<PatientUtilization> dbPatientUtilizations = patientUtilizationRepository.findByBenefitCodeAndPatient(code, crId);

        Optional<List<Member>> memberOptional = memberRepository.findByCrId(crId);

        boolean memberPresent = memberOptional.isPresent() && !memberOptional.get().isEmpty();

        Member member;

        if(!memberPresent){
            member = fetchPatientFromHIE (crId);
        }
        else {
            member = memberOptional.get().getFirst();
        }

        fillMemberCoverage(member);

        if(UNLIMITED_PACKAGES.contains(code.toLowerCase())) {
            throw new NoLimitException("Patient Utilization Balance is LimitLess for code:"+code);
        }

        List<BenefitsMaster> benefitsMasters = benefitsMasterRepository.findByCodeOrInterventionCode(code , code);

        if(benefitsMasters.isEmpty()){
            throw new BadRequestException("Benefit Package Requested is Invalid:"+code);
        }

        //Map<String , PatientUtilization> utilizationMap = dbPatientUtilizations.stream().collect(Collectors.toMap(utilization -> utilization.getBenefitsMaster().getInterventionCode() , Function.identity()));

        List<PatientUtilization> patientUtilizations =  benefitsMasters.stream().map(benefitsMaster -> {
//            if (utilizationMap.containsKey(benefitsMaster.getInterventionCode())){
//                return utilizationMap.get(benefitsMaster.getInterventionCode());
//            }
//            else {
                return provideRecord(member, benefitsMaster , memberPresent);
            //}
        }).toList();

        return toPatientUtilizationResponse(patientUtilizations);

        //dbPatientUtilizations.forEach(patientUtilizations::remove);

        //saveDataAsync(patientUtilizations);

        //return responses;
    }

    private List<PatientUtilizationResponse> provideIndividualRecord(String crId, String code){
        //TODO : we are not saving Utilization details in database
//        List<PatientUtilization> patientUtilization = patientUtilizationRepository.findByBenefitCodeAndPatient(code, crId);
//        if (!patientUtilization.isEmpty()) {
//            return toPatientUtilizationResponse(patientUtilization);
//        }

        Optional<List<Member>> memberOptional = memberRepository.findByCrId(crId);

        boolean memberPresent = memberOptional.isPresent() && !memberOptional.get().isEmpty();

        Member member;

        if(!memberPresent){
            member = fetchPatientFromHIE(crId);
        }
        else {
            member = memberOptional.get().getFirst();
        }

        fillMemberCoverage(member);

        if(UNLIMITED_PACKAGES.stream().anyMatch(benefit -> code.toLowerCase().startsWith(benefit))) {
            throw new NoLimitException("Patient Utilization Balance is LimitLess for code:"+code);
        }

        List<BenefitsMaster> benefitsMaster = benefitsMasterRepository.findByCodeOrInterventionCode(code , code);

        if(benefitsMaster.isEmpty()){
            throw new BadRequestException("Benefit Sub-Package requested in Invalid :"+code);
        }

        if(benefitsMaster.size() > 1){
            throw new BadRequestException("Benefits Record contains multiple records for requested code :"+code);
        }

        PatientUtilization utilization = provideRecord(member , benefitsMaster.getFirst() , memberPresent);

        //saveDataAsync(utilization);

        return toPatientUtilizationResponse(List.of(utilization));
    }

    private PatientUtilization provideRecord(Member member , BenefitsMaster benefitsMaster , boolean memberPresent){

        PatientLimit policyPatientLimit = null;

        LocalDate policyStartDate = member.getPolicyStartDate() != null ? member.getPolicyStartDate().toLocalDate() : null;
        LocalDate policyEndDate = member.getPolicyEndDate() != null ? member.getPolicyEndDate().toLocalDate() : null;

        if(memberPresent) {
            if (benefitsMaster.isNeedsPreAuth()) {
                policyPatientLimit = claimsHandlerService.fetchLimits(
                        member.getHouseholdNumber(),
                        member.getCrId(),
                        Constants.Use.PREAUTHORIZATION,
                        benefitsMaster.getCode(),
                        benefitsMaster.getInterventionCode(),
                        policyStartDate,
                        policyEndDate,
                        null,
                        true);
            } else {
                policyPatientLimit = claimsHandlerService.fetchLimits(
                        member.getHouseholdNumber(),
                        member.getCrId(),
                        Constants.Use.CLAIM,
                        benefitsMaster.getCode(),
                        benefitsMaster.getInterventionCode(),
                        policyStartDate,
                        policyEndDate,
                        null,
                        true);
            }
        }

        List<FundType> applicableFunds = extractApplicableScheme(benefitsMaster.getAppliesToSchemes());

        List<FundUtilizationLimit> fundList = extractFundAmounts(applicableFunds,policyPatientLimit.getUtilisedAmount() , benefitsMaster.getInterventionCode() );

        PatientUtilization utilization = PatientUtilization.builder()
                .patient(member)
                .benefitsMaster(benefitsMaster)
                .individualUtilisedLimit(policyPatientLimit.getIndividualUtilisedLimit() -1)
                .householdUtilisedLimit(policyPatientLimit.getHouseholdUtilisedLimit() -1)
                .fundUtilizationLimits(fundList)
                .build();

        fundList.forEach(f -> f.setUtilization(utilization));

        if(!benefitsMaster.getUtilizationPeriod().equals(UtilizationPeriod.YEARLY)){

            PatientLimit periodPatientLimit = null;
            LocalDate periodStartDate = UtilizationPeriod.getStartOfPeriod(benefitsMaster.getUtilizationPeriod());
            LocalDate periodEndDate = UtilizationPeriod.getEndOfPeriod(benefitsMaster.getUtilizationPeriod());

            if (benefitsMaster.isNeedsPreAuth()) {
                periodPatientLimit = claimsHandlerService.fetchLimits(
                        member.getHouseholdNumber(),
                        member.getCrId(),
                        Constants.Use.PREAUTHORIZATION,
                        benefitsMaster.getCode(),
                        benefitsMaster.getInterventionCode(),
                        periodStartDate,
                        periodEndDate,
                        null,
                        false);
            } else {
                periodPatientLimit = claimsHandlerService.fetchLimits(
                        member.getHouseholdNumber(),
                        member.getCrId(),
                        Constants.Use.CLAIM,
                        benefitsMaster.getCode(),
                        benefitsMaster.getInterventionCode(),
                        periodStartDate,
                        periodEndDate,
                        null,
                        false);
            }

            LocalDate lastUsageDate;
            List<String> groupCodes = List.of(benefitsMaster.getInterventionCode());

            if(StringUtils.isNotBlank(benefitsMaster.getSubPackage())){
                groupCodes = benefitsMasterRepository.findInterventionCodesBySubPackage(benefitsMaster.getSubPackage())
                        .orElse(List.of(benefitsMaster.getInterventionCode()));
            }

            if(benefitsMaster.isNeedsPreAuth()){
                lastUsageDate = interventionValidationService.getLastUsageDate(
                        member.getCrId(),
                        groupCodes,
                        new ArrayList<>(PRE_AUTH_APPROVED_STATUS),
                        List.of(Constants.Use.PREAUTHORIZATION , Constants.Use.CLAIM));
            }
            else{
                lastUsageDate = interventionValidationService.getLastUsageDate(
                        member.getCrId(),
                        groupCodes,
                        new ArrayList<>(APPROVED_STATUS),
                        List.of(Constants.Use.CLAIM));
            }

            utilization.setIndividualUtilisedDuringPeriod(periodPatientLimit.getIndividualUtilisedLimit() - 1);
            utilization.setHouseholdUtilisedDuringPeriod(periodPatientLimit.getHouseholdUtilisedLimit() - 1);
            utilization.setIndividualMaxDuringPeriod(benefitsMaster.getPeriodCount());
            utilization.setLastUsageDate(lastUsageDate);
        }

        if(benefitsMaster.getIsPerDiemClub()){
            Integer count = interventionValidationService.calculateUtilizedPerDiem(member.getHouseholdNumber() , policyStartDate, policyEndDate);
            utilization.setUtilizedPerDiem(count);
        }

        utilization.setPeriod(benefitsMaster.getUtilizationPeriod());

        return utilization;
    }

    private List<PatientUtilizationResponse.FundUtilizationResponse> toFundUtilizationResponse(List<FundUtilizationLimit> fundList , String code) {

        return fundList.stream().map(fund -> {
                    BigDecimal maxAmount = getMaxFundLimit(fund.getFundType() , code);
            return PatientUtilizationResponse.FundUtilizationResponse.builder()
                            .fundType(fund.getFundType().name())
                            .utilisedAmount(fund.getUtilisedAmount())
                            .maxAmount(maxAmount)
                            .availableAmount(maxAmount != null ? maxAmount.subtract(fund.getUtilisedAmount()) : null)
                            .build();
                })
                .toList();
    }

    private List<PatientUtilizationResponse> toPatientUtilizationResponse(List<PatientUtilization> utilizations) {
        return utilizations.stream().map(this::toPatientUtilizationResponse).toList();
    }

    private PatientUtilizationResponse toPatientUtilizationResponse(PatientUtilization utilization) {

        InterventionUtilizationLimit utilizationLimit = limitsMap.get(utilization.getBenefitsMaster().getInterventionCode());

        PatientUtilizationResponse.IntermediatePeriodUsage intermediatePeriodUsage = null;
        if(utilization.getIndividualUtilisedDuringPeriod()!= null) {
            intermediatePeriodUsage = PatientUtilizationResponse.IntermediatePeriodUsage.builder()
                    .individualUtilisedDuringPeriod(utilization.getIndividualUtilisedDuringPeriod())
                    .period(utilization.getPeriod())
                    .lastUsageDate(utilization.getLastUsageDate())
                    .individualMaxDuringPeriod(utilization.getIndividualMaxDuringPeriod())
                    .build();
        }

        PatientUtilizationResponse.PerDiemUsage perDiemUsage = null;
        if(utilization.getUtilizedPerDiem()!= null) {
            perDiemUsage = PatientUtilizationResponse.PerDiemUsage.builder()
                    .maxPerDiemCount(MAX_PER_DIEM)
                    .utilisedPerDiemCount(utilization.getUtilizedPerDiem())
                    .build();
        }

        PatientUtilizationResponse response =  PatientUtilizationResponse.builder()
                .crId(utilization.getPatient().getCrId())
                .code(utilization.getBenefitsMaster().getInterventionCode())
                .name(utilization.getBenefitsMaster().getInterventionName())
                .limitScope(LimitScope.fromString(utilization.getBenefitsMaster().getLimitsIndividualHousehold()))
                .individualMaxLimit(utilizationLimit == null ||utilizationLimit.getIndividualUtilizationCount()== null ? null : utilizationLimit.getIndividualUtilizationCount())
                .householdMaxLimit(utilizationLimit == null || utilizationLimit.getHouseholdUtilizationCount() == null ? null : utilizationLimit.getHouseholdUtilizationCount())
                .individualUtilisedLimit(utilization.getIndividualUtilisedLimit())
                .householdUtilisedLimit(utilization.getHouseholdUtilisedLimit())
                .fundUtilizationLimit(toFundUtilizationResponse(utilization.getFundUtilizationLimits() , utilization.getBenefitsMaster().getInterventionCode()))
                .intermediatePeriodUsage(intermediatePeriodUsage)
                .perDiemUsage(perDiemUsage)
                .build();

        PatientUtilizationResponse.ComputationalDetail detail = PatientUtilizationResponse.ComputationalDetail.builder()
                .individualLimitAvailableCount(response.getIndividualMaxLimit()== null ?null : response.getIndividualMaxLimit() - response.getIndividualUtilisedLimit())
                .householdLimitAvailableCount(response.getHouseholdMaxLimit() == null ? null : response.getHouseholdMaxLimit() - response.getHouseholdUtilisedLimit())
                .limitAvailableAmount(calculateLimitAvailableAmount(response.getFundUtilizationLimit()))
                .coverageStartDate(utilization.getPatient().getCoverageStartDate())
                .coverageEndDate(utilization.getPatient().getCoverageEndDate())
                .policyStartDate(utilization.getPatient().getPolicyStartDate())
                .policyEndDate(utilization.getPatient().getPolicyEndDate())
                .perDiemAvailableCount(perDiemUsage != null ? perDiemUsage.getMaxPerDiemCount() - perDiemUsage.getUtilisedPerDiemCount() : null)
                .intermediatePeriodAvailableCount(intermediatePeriodUsage != null ? intermediatePeriodUsage.getIndividualMaxDuringPeriod() - intermediatePeriodUsage.getIndividualUtilisedDuringPeriod() : null)
                .build();

        boolean eligibility = validateEligibility(detail);
        detail.setEligibility(eligibility);
        LocalDate nextAvailableDate = UtilizationPeriod.computeNextAvailableDate(eligibility, utilization.getLastUsageDate() ,  utilization.getPatient().getPolicyEndDate().toLocalDate() , utilization.getPeriod());
        detail.setNextAvailableDate(nextAvailableDate);
        response.setComputationalDetail(detail);

        return response;
    }

    private List<FundType> extractApplicableScheme(String schemes) {

        if (StringUtils.isBlank(schemes)) {
            return Collections.emptyList();
        }

        // Create a lookup map for better performance
        Map<String, FundType> displayNameToFundType = Arrays.stream(FundType.values())
                .collect(Collectors.toMap(ft -> ft.name.toLowerCase(), ft -> ft));

        return Arrays.stream(schemes.split(COMMA)).map(String::trim)
                .map(String::toLowerCase)
                .map(displayNameToFundType::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Async
    public void saveDataAsync(PatientUtilization utilization) {
        try {
            patientUtilizationRepository.save(utilization);
        } catch (Exception e) {
            log.info("Error occurred while saving data: {}", e.getMessage(), e);
        }
    }

    @Async
    public void saveDataAsync(List<PatientUtilization> utilizations) {
        try {
            patientUtilizationRepository.saveAll(utilizations);
        } catch (Exception e) {
            log.info("Error occurred while saving data: {}", e.getMessage(), e);
        }
    }

    private Integer extractNumber(String numberStr) {

        if (numberStr == null || numberStr.trim().isEmpty()) {
            return null;
        }

        String value = numberStr.trim();
        // Case: greater than -> return null
        if (value.startsWith(">")) {
            return null;
        }

        return Optional.ofNullable(numberStr)
                .filter(StringUtils::isNotBlank)
                .map(str -> str.replaceAll("[^0-9]", ""))
                .filter(StringUtils::isNotBlank)
                .map(Integer::valueOf)
                .orElse(null);
    }

    private List<FundUtilizationLimit> extractFundAmounts( List<FundType> fundTypes, BigDecimal claimedAmount , String code) {
        if(fundTypes.isEmpty()){
            return Collections.emptyList();
        }
        List<FundUtilizationLimit> fundUtilizationLimits = new ArrayList<>();

        if(fundTypes.contains(FundType.SHA) || fundTypes.contains(FundType.SHIF)){
            BigDecimal shifAmount = findMin(claimedAmount,getMaxFundLimit(FundType.SHA , code));
            FundUtilizationLimit fundUtil = new FundUtilizationLimit();
            fundUtil.setFundType(FundType.SHIF);
            fundUtil.setUtilisedAmount(shifAmount);
            fundUtilizationLimits.add(fundUtil);
            claimedAmount = claimedAmount.subtract(shifAmount);
        }
        if(fundTypes.contains(FundType.ECCIF)){
            BigDecimal eccifAmount = findMin(claimedAmount,getMaxFundLimit(FundType.ECCIF , code));
            FundUtilizationLimit fundUtil = new FundUtilizationLimit();
            fundUtil.setFundType(FundType.ECCIF);
            fundUtil.setUtilisedAmount(eccifAmount);
            fundUtilizationLimits.add(fundUtil);
//            claimedAmount = claimedAmount.subtract(eccifAmount);
        }
//        if(fundTypes.contains(FundType.PMSF)){
//            FundUtilizationLimit fundUtil = new FundUtilizationLimit();
//            fundUtil.setFundType(FundType.PMSF);
//            fundUtil.setUtilisedAmount(claimedAmount);
//            fundUtilizationLimits.add(fundUtil);
//        }

        return fundUtilizationLimits;
    }

    private BigDecimal getMaxFundLimit(FundType fundType , String code){
        switch (fundType) {
            case SHA ->  {
                return Optional.ofNullable(limitsMap.get(code))
                        .map(InterventionUtilizationLimit::getUtilizationLimit)
                        .orElse(null);
            }
            case SHIF ->  {
                return Optional.ofNullable(limitsMap.get(code))
                        .map(InterventionUtilizationLimit::getUtilizationLimit)
                        .orElse(null);
            }
            case ECCIF -> {
                return eccifFundLimit;
            }
            default -> {
                return null;
            }
        }
    }

    private BigDecimal findMin(BigDecimal val1, BigDecimal val2) {
        if (val1 == null && val2 == null) {
            return null;
        }
        if (val1 == null) {
            return val2;
        }
        if (val2 == null) {
            return val1;
        }

        return val1.min(val2);
    }

    private void fillMemberCoverage(Member member){

        if(member.getCoverageStartDate() != null &&
                member.getCoverageEndDate() != null &&
                member.getCoverageStartDate().isBefore(LocalDate.now()) &&
                member.getCoverageEndDate().isAfter(LocalDate.now()) &&
                member.getPolicyStartDate() != null &&
                member.getPolicyEndDate() != null &&
                member.getPolicyStartDate().isBefore(LocalDateTime.now()) &&
                member.getPolicyEndDate().isAfter(LocalDateTime.now())
        ){
            return;
        }

        // Use FinsprintHelper to get coverage period with V5 API and scheme selection
        CoveragePeriodDto coveragePeriod = finsprintHelper.checkEligibilityFromMember(member,false, LocalDate.now(), LocalDate.now());

        if(coveragePeriod == null ||
                coveragePeriod.getCoverageStartDate() == null ||
                coveragePeriod.getCoverageEndDate() == null ||
                coveragePeriod.getPolicyStartDate() == null ||
                coveragePeriod.getPolicyEndDate() == null){
            throw new DataIntegrityViolationException("Eligibility Information not found for crId: " + member.getCrId());
        }

        LocalDate today = LocalDate.now();

        if(coveragePeriod.getCoverageStartDate().isAfter(today)) {
            throw new DataValidationException("Eligibility Coverage of Patient will start after :" + coveragePeriod.getCoverageStartDate());
        }

        if(coveragePeriod.getCoverageEndDate().isBefore(today)){
            throw new DataValidationException("Eligibility Coverage of Patient is expired on :" + coveragePeriod.getCoverageEndDate());
        }

        if(coveragePeriod.getPolicyStartDate().isAfter(today)) {
            throw new DataValidationException("Eligibility Policy of Patient will start after :" + coveragePeriod.getPolicyStartDate());
        }

        if(coveragePeriod.getPolicyEndDate().isBefore(today)){
            throw new DataValidationException("Eligibility Policy of Patient is expired on :" + coveragePeriod.getPolicyEndDate());
        }

        member.setCoverageStartDate(coveragePeriod.getCoverageStartDate());
        member.setCoverageEndDate(coveragePeriod.getCoverageEndDate());
        member.setPolicyStartDate(coveragePeriod.getPolicyStartDate().atStartOfDay());
        member.setPolicyEndDate(coveragePeriod.getPolicyEndDate().atStartOfDay());

        memberRepository.save(member);
    }

    private BigDecimal calculateLimitAvailableAmount(List<PatientUtilizationResponse.FundUtilizationResponse> fundUtilizationLimits){
        if(fundUtilizationLimits == null || fundUtilizationLimits.isEmpty()){
            return null;
        }
        if(fundUtilizationLimits.stream().anyMatch(fund -> fund.getFundType().equalsIgnoreCase(PMSF.name()))){
            return null;
        }

        return fundUtilizationLimits.stream()
                .filter(fundUtilizationLimit -> Objects.nonNull(fundUtilizationLimit.getMaxAmount()))
                .map(fundUtilizationLimit -> (fundUtilizationLimit.getUtilisedAmount() != null) ?
                        fundUtilizationLimit.getMaxAmount().subtract(fundUtilizationLimit.getUtilisedAmount()): BigDecimal.ZERO)
                .reduce(BigDecimal::add).orElse(null);
    }

    private Boolean validateEligibility(PatientUtilizationResponse.ComputationalDetail detail) {
        if (detail == null) {
            return false;
        }
        if (detail.getCoverageStartDate().isAfter(LocalDate.now())) {
            return false;
        }
        if (detail.getCoverageEndDate().isBefore(LocalDate.now())) {
            return false;
        }
        if (detail.getPolicyStartDate().isAfter(LocalDateTime.now())) {
            return false;
        }
        if (detail.getPolicyEndDate().isBefore(LocalDateTime.now())) {
            return false;
        }
        if (detail.getIndividualLimitAvailableCount() != null && detail.getIndividualLimitAvailableCount() <= 0) {
            return false;
        }
        if (detail.getHouseholdLimitAvailableCount() != null && detail.getHouseholdLimitAvailableCount() <= 0) {
            return false;
        }
        if (detail.getLimitAvailableAmount() != null && detail.getLimitAvailableAmount().compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        if (detail.getPerDiemAvailableCount() != null && detail.getPerDiemAvailableCount() <= 0) {
            return false;
        }
        if(detail.getIntermediatePeriodAvailableCount() != null && detail.getIntermediatePeriodAvailableCount() <= 0){
            return false;
        }
        return true;
    }

    private Member fetchPatientFromHIE(String crId) {
        String patientJson = hieHelper.getPatientFromHieViaShaNumber(crId);
        Patient patientHie = convertToFhirPatient(crId , patientJson);
        // 2. Save/update to DB/FHIR
        return saveDataPatient(patientJson, patientHie, crId);
    }

    private Patient convertToFhirPatient(String crId ,String patientJson) {
        if (StringUtils.isBlank(patientJson)) {
            log.warn("Beneficiary records not found in HIE, Reference ID : {}", crId);
            throw new PatientNotFoundException(crId);
        } else {
            // Parse patient JSON, save to DB, and store in FHIR if needed
            return fhirContext.newJsonParser().parseResource(Patient.class, patientJson);
        }
    }

    private Member saveDataPatient(String patientJson, Patient patientHie, String patientId) {
        Member member = memberService.saveToDatabase(Member.fromJsonBundle(patientJson));
        log.info("Patient : {}  in FHIR. Creating/updating a entry...", patientId);
        MethodOutcome outcome = fhirClient.update().resource(patientHie).execute();
        log.info("Created/updated Patient resource ID: {}", outcome.getId());
        return member;
    }

    public List<DropDown> getSearchBenefits(String searchValue , boolean filter) {
        boolean hasExactlyTwoHyphens = searchValue.matches("^[A-Za-z]+-\\d{2}-.*$");

        List<DropDown> result= null;

        if (!hasExactlyTwoHyphens) {
            result = benefitsMasterRepository.findBenefitMasterPackageRecords(searchValue);
        }
        else{
            result = benefitsMasterRepository.findBenefitMasterSubPackageRecords(searchValue);
        }

        if (filter) {
            return result.stream().filter(benefit -> !UNALLOWED_CODES.contains(benefit.getCode()))
                .collect(Collectors.toList());
        }

        return result;
    }

    public PatientUtilizationResponseDto getUtilizationBalanceForIndividual(
            String claimId,
            String shaCode,
            String use) {
        try {
            // Fetch the anchor claim
            List<InsuranceClaim> claimList = claimsRepository.findByClaimIdAndUse(
                            UUID.fromString(claimId), use)
                    .orElseThrow(() -> new DataValidationException("Claim not found: " + claimId));

            InsuranceClaim anchor = claimList.get(0);
            UUID patientId = anchor.getPatient().getId();
            LocalDateTime anchorCreatedAt = anchor.getCreationDate();

            log.info("Fetching utilization for patient: {}, shaCode: {}, anchorDate: {}",
                    patientId, shaCode, anchorCreatedAt);

            LocalDateTime policyStartDate = anchor.getPolicyStartDate();
            LocalDateTime policyEndDate = anchor.getPolicyEndDate();

            // Fetch data
            List<ClaimUtilizationProjection> claimRecords =
                    claimsRepository.findClaimUtilizationHistory(patientId, shaCode, anchorCreatedAt, policyStartDate, policyEndDate);

            List<PreAuthUtilizationProjection> preAuthRecords =
                    claimsRepository.findPreAuthUtilizationHistory(patientId, shaCode, anchorCreatedAt, policyStartDate, policyEndDate);

            // Create a map for easy lookup
            Map<UUID, PatientUtilizationItemDto> resultMap = new LinkedHashMap<>();

            // 1. Process all Pre-Authorizations first
            for (PreAuthUtilizationProjection preAuth : preAuthRecords) {

                PatientUtilizationItemDto dto = PatientUtilizationItemDto.builder()
                        .preAuthId(preAuth.getPreAuthId())
                        .preAuthServiceDate(preAuth.getPreAuthServiceDate())
                        .preAuthAmount(safeBigDecimal(preAuth.getPreAuthAmount()))
                        .preAuthStatus(preAuth.getPreAuthStatus())
                        .preAuthcreatedDate(preAuth.getPreauthCreatedDate())

                        .claimId(preAuth.getClaimId())
                        .claimServiceDate(preAuth.getClaimServiceDate())
                        .claimAmount(safeBigDecimal(preAuth.getClaimAmount()))
                        .claimStatus(preAuth.getClaimStatus())
                        .claimCreatedDate(preAuth.getClaimCreatedDate())

                        .shifAmount(safeBigDecimal(preAuth.getShifAmount()))
                        .eccifAmount(safeBigDecimal(preAuth.getEccifAmount()))
                        .pmfAmount(safeBigDecimal(preAuth.getPmfAmount()))

                        .utilizedAmount(calculateUtilizedAmount(preAuth))
                        .build();

                resultMap.put(preAuth.getPreAuthId(), dto);
            }

            // 2. Process Claims and link them
            for (ClaimUtilizationProjection claim : claimRecords) {
                log.debug("Processing claim: {}, preAuthId: {}",
                        claim.getClaimId(), claim.getPreAuthId());

                UUID preAuthId = claim.getPreAuthId();

                if (preAuthId != null && resultMap.containsKey(preAuthId)) {
                    // Update existing pre-auth with claim details
                    PatientUtilizationItemDto dto = resultMap.get(preAuthId);
                    updateDtoWithClaim(dto, claim);
                } else {
                    // This is a standalone claim (or pre-auth not found)
                    PatientUtilizationItemDto dto = PatientUtilizationItemDto.builder()
                            .claimId(claim.getClaimId())
                            .claimServiceDate(claim.getClaimServiceDate())
                            .claimAmount(safeBigDecimal(claim.getClaimAmount()))
                            .claimStatus(claim.getClaimStatus())
                            .claimCreatedDate(claim.getClaimCreatedDate())
                            .preAuthId(claim.getPreAuthId())
                            .shifAmount(safeBigDecimal(claim.getShifAmount()))
                            .eccifAmount(safeBigDecimal(claim.getEccifAmount()))
                            .pmfAmount(safeBigDecimal(claim.getPmfAmount()))
                            .utilizedAmount(safeBigDecimal(claim.getClaimAmount()))
                            .build();

                    // Use claimId as key for standalone claims
                    resultMap.put(claim.getClaimId(), dto);
                }
            }

            // 3. Sort by date
            List<PatientUtilizationItemDto> items = new ArrayList<>(resultMap.values());

            items.sort(Comparator.comparing(
                    dto -> {
                        // Prefer claim date, fallback to pre-auth date
                        return dto.getClaimCreatedDate() != null ?
                                dto.getClaimCreatedDate() : dto.getPreAuthcreatedDate();
                    },
                    Comparator.nullsLast(Comparator.reverseOrder())
            ));

            return PatientUtilizationResponseDto.builder()
                    .crId(anchor.getPatient().getCrId())
                    .shaCode(shaCode)
                    .policyStartDate(anchor.getPolicyStartDate())
                    .policyEndDate(anchor.getPolicyEndDate())
                    .claim(items)
                    .build();
        } catch (Exception e) {
            throw new DataValidationException("An unexpected error has occurred. Please try again in sometimes.", e);
        }
    }

    // Renamed from nvl to avoid conflict
    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal calculateUtilizedAmount(PreAuthUtilizationProjection preAuth) {
        // If there's a linked claim, use claim amount, otherwise use pre-auth amount
        if (preAuth.getClaimId() != null && preAuth.getClaimAmount() != null) {
            return safeBigDecimal(preAuth.getClaimAmount());
        }
        return safeBigDecimal(preAuth.getPreAuthAmount());
    }

    private void updateDtoWithClaim(PatientUtilizationItemDto dto, ClaimUtilizationProjection claim) {
        dto.setClaimId(claim.getClaimId());
        dto.setClaimServiceDate(claim.getClaimServiceDate());
        dto.setClaimAmount(safeBigDecimal(claim.getClaimAmount()));
        dto.setClaimStatus(claim.getClaimStatus());
        dto.setClaimCreatedDate(claim.getClaimCreatedDate());
        dto.setUtilizedAmount(safeBigDecimal(claim.getClaimAmount()));

        // Update amounts from claim (they might be different)
        dto.setShifAmount(safeBigDecimal(claim.getShifAmount()));
        dto.setEccifAmount(safeBigDecimal(claim.getEccifAmount()));
        dto.setPmfAmount(safeBigDecimal(claim.getPmfAmount()));
    }
}
