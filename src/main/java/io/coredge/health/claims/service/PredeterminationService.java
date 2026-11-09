package io.coredge.health.claims.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.benefits.DocumentType;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.db.repositories.PredeterminationLogRepository;
import io.coredge.health.claims.enums.AdjudicationParamsMessage;
import io.coredge.health.claims.enums.IdentifierType;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.helpers.ClaimValidationUtility;
import io.coredge.health.claims.helpers.PatientHelper;
import io.coredge.health.claims.models.Predetermination;
import io.coredge.health.claims.db.entity.PredeterminationLog;
import io.coredge.health.claims.models.PredeterminationLogResponse;
import io.coredge.health.claims.models.RuleInputDTO;
import io.coredge.health.claims.models.intervention.dto.ClaimUsage;
import io.coredge.health.claims.service.intervention.InterventionValidationService;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.APPROVED_EXTENSION_STATES;
import static io.coredge.health.claims.constants.Constants.CLAIM_APPROVED_EXTENSION_STATES;
import static io.coredge.health.claims.constants.Constants.SubType.*;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_HOUSE_HOLD_USE_CNT;
import static org.hl7.fhir.r4.model.Claim.Use.CLAIM;

@Slf4j
@Service
public class PredeterminationService {

    private final DMNService dmnService;
    private final RuleProcessingService ruleProcessingService;
    private final InsuranceClaimsRepository claimsRepository;
    private final ObjectMapper objectMapper;
    private final PredeterminationLogRepository logRepository;
    private final ClaimBundleService claimBundleService;
    private final BenefitMasterService benefitMasterService;
    private final InterventionValidationService interventionValidationService;
    private final PagedResourcesAssembler pagedResourcesAssembler;

    public PredeterminationService(DMNService dmnService, RuleProcessingService ruleProcessingService,
            InsuranceClaimsRepository claimsRepository, ObjectMapper objectMapper,
            PredeterminationLogRepository logRepository,
            ClaimBundleService claimBundleService, BenefitMasterService benefitMasterService,
            InterventionValidationService interventionValidationService,
            PagedResourcesAssembler pagedResourcesAssembler) {
        this.dmnService = dmnService;
        this.ruleProcessingService = ruleProcessingService;
        this.objectMapper = objectMapper;
        this.claimsRepository = claimsRepository;
        this.logRepository = logRepository;
        this.claimBundleService = claimBundleService;
        this.benefitMasterService = benefitMasterService;
        this.interventionValidationService = interventionValidationService;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
    }

    public Map<String, Object> responsePredetermination(Predetermination data) {
        List<String> responses = validatePredetermination(data);
        PredeterminationLog dataLog = PredeterminationLog.builder()
                .claimId(data.getClaimId())
                .input(data)
                .reason(responses)
                .build();
        dataLog.setCreatedBy(HealthIdContextHolder.userId());
        dataLog.setCreationDate(LocalDateTime.now());
        if (responses.isEmpty()) {
            log.info("Predetermination prod:: PatientId {} with Approve", data.getPatientId());
            dataLog.setStatus(true);
        } else {
            log.warn("Predetermination prod:: PatientId {} with Rejected", data.getPatientId());
            dataLog.setStatus(false);

        }
        logRepository.save(dataLog);
        return Map.of("status", dataLog.isStatus(), "reason", responses);
    }

    public List<String> validatePredetermination(Predetermination data) {

        List<String> errors = new ArrayList<>();

        validateDocumentTypes(data, errors);

        Map<String, Object> params = retrieveValidationConstraints(data, errors);
        Map<String, String> ruleByCode = dmnService.getRuleJsonByCode(data.getCode(), CLAIM,
                DMNService.filtersData(params));
        if (ruleByCode.isEmpty()) {
            errors.add("No Valid Rule Found for the given code: " + data.getCode());
            return errors;
        }
        Map<String, Object> rules = ruleByCode.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, RuleInputDTO> response = ruleProcessingService.processRules(rules, params, "");

        response.entrySet().stream().filter(entry -> entry.getValue() != null && "FAIL".equals(entry.getValue().getResult()))
                .filter(entry -> AdjudicationParamsMessage.fromValue(entry.getKey()) != null)
                .forEach(entry -> errors.add(AdjudicationParamsMessage.createMessage(AdjudicationParamsMessage.fromValue(entry.getKey()), entry.getValue().getRule().toString(), entry.getValue().getInput().toString())));
        return errors;
    }

    private Map<String, Object> retrieveValidationConstraints(Predetermination data, List<String> errors) {

        Map<String, Object> params = new HashMap<>();

        // Sequence
        if (StringUtils.isBlank(data.getSequence())) {
            params.put(VAR_SEQUENCE, 1.0);
        }

        // Service Category
        if (data.getServiceCategory().equalsIgnoreCase(INPATIENT))
            params.put(VAR_SERVICE_CATEGORY, "inpatient");
        else if (data.getServiceCategory().equalsIgnoreCase(OUTPATIENT))
            params.put(VAR_SERVICE_CATEGORY, "outpatient");
        else if (data.getServiceCategory().equalsIgnoreCase(EMERGENCY))
            params.put(VAR_SERVICE_CATEGORY, "emergency");

        // Age
        int age = FhirHelper.calculateAge(data.getBirthDate());
        if (age > 200) {
            errors.add("Age:" + age + " is greater than defined limit");
        }
        params.put(VAR_AGE, age);

        // Intervention Code
        if (!ClaimValidationUtility.isValidProductOrServiceCode(data.getCode(), benefitMasterService)) {
            errors.add("Invalid Product or Service Code: " + data.getCode());
        }

        // Billable Period
        if (data.getBillableStartDate().after(data.getBillableEndDate())) {
            errors.add("Billable Start Date is greater than Billable End Date");
        }

        if(FhirHelper.convertToLocalDateFromDateTime(data.getBillableEndDate()).isAfter(LocalDate.now())){
            errors.add(String.format("Billable End Date %s would not be greater than Current Date %s",data.getBillableEndDate() , LocalDate.now()));
        }

        // LOS calculate
        params.put(VAR_LOS, FhirHelper.calculateLoS(data.getBillableStartDate(), data.getBillableEndDate()));

        // HIE Patient Validation
        Patient hiePatient = null;
        try {
            hiePatient = claimBundleService.getPatientWithHieServer(data, "CR ID");
            errors.addAll(claimBundleService.validatePatientWithHieServer(data, hiePatient));
        } catch (Exception e) {
            errors.add(e.getMessage());
        }

        //HIE Provider Validation
        Optional.ofNullable(claimBundleService.validateProviderWithHieServer(data.getProviderId(), IdentifierType.FR_CODE, data.getFacilityCategory())).ifPresent(errors::add);

        ClaimUsage usage = interventionValidationService.buildClaimUsage(data.getBillableStartDate(), data.getBillableEndDate(), data.getCode(), data.getBirthDate() , data.getClaimAmount(), CLAIM.getDisplay());


        String patientHouseHold = PatientHelper.extractHouseholdFromPatient(hiePatient);

        LocalDate serviceDate = data.getBillableStartDate().toInstant()
                .atZone(ZoneId.of("Africa/Nairobi"))
                .toLocalDate();

        LocalDate endServiceDate = data.getBillableEndDate().toInstant()
                .atZone(ZoneId.of("Africa/Nairobi"))
                .toLocalDate();
        //Rule Validation
        try {
            interventionValidationService.validateInterventionRules(patientHouseHold, serviceDate, endServiceDate, usage, data.getFacilityCategory() , data.getPatientId(), false, null, null);
        } catch (Exception e) {
            errors.add(e.getMessage());
        }

        if (hiePatient == null || StringUtils.isBlank(hiePatient.getIdPart()) || StringUtils.isBlank(patientHouseHold)) {
            errors.add("Patient data or household information is missing or incomplete");
        } else {
            int intUseCount = claimsRepository.countActiveClaimsByMemberCrIdAndCode(hiePatient.getIdPart(),
                    APPROVED_EXTENSION_STATES, Arrays.asList(data.getCode()), Constants.Use.CLAIM, null, null, null);
            int householdUsageCnt = claimsRepository.countActiveClaimsByHouseholdAndCode(patientHouseHold,
                    APPROVED_EXTENSION_STATES, Arrays.asList(data.getCode()), Constants.Use.CLAIM, null, null, null);
            // ToDo : check if date range is needed here
            BigDecimal totalAmountUsedEarlier = claimsRepository.getTotalAmtOfClaimsByMemberCrIdAndCode(hiePatient.getIdPart(),
                    APPROVED_EXTENSION_STATES, data.getCode(), Constants.Use.CLAIM, null, null);

            double totalAmountIncludingCurrent = totalAmountUsedEarlier.longValue() + data.getClaimAmount();
            params.put(VAR_INTERVENTION_USE_CNT, intUseCount + 1);
            params.put(VAR_HOUSE_HOLD_USE_CNT, householdUsageCnt + 1);
            params.put(VAR_INDIVIDUAL_USAGE, totalAmountIncludingCurrent);
        }

        if (!params.containsKey(VAR_HOUSE_HOLD_USE_CNT) && StringUtils.isNotBlank(data.getHouseholdNumber())) {
            int householdUsageCnt = claimsRepository.countActiveClaimsByHouseholdAndCode(data.getHouseholdNumber(),
                    APPROVED_EXTENSION_STATES, Arrays.asList(data.getCode()), Constants.Use.CLAIM, null, null, null);
            householdUsageCnt++;
            params.put(VAR_HOUSE_HOLD_USE_CNT, householdUsageCnt);
        } else {
            params.put(VAR_HOUSE_HOLD_USE_CNT, 0);
        }

        // preAuth Validate
        if (Boolean.TRUE.equals(data.getHasPreAuth())) {
            if (StringUtils.isNotBlank(data.getPreAuthRef())) {

                Optional.ofNullable(claimBundleService.validatePreAuthClaims(data)).ifPresent(errors::addAll);
            } else {
                errors.add("PreAuth Ref id not found for hasPreAuth Check");
            }
        }

        Map<String, Object> map = objectMapper.convertValue(data, new TypeReference<>() {
        });
        params.putAll(map);
        return params;
    }

    public CollectionModel<EntityModel<PredeterminationLogResponse>> fetchPredeterminationDetails(String claimId, Boolean status, PageRequest pagable) {

        Page<PredeterminationLog> predeterminationLogs = logRepository.fetchPredeterminationDetails(claimId, status, pagable);
        if (predeterminationLogs.isEmpty()) {
            throw new DataNotFoundException("No Data found for Predetermination Log Request");
        }

        Page<PredeterminationLogResponse> responses = predeterminationLogs.map(this::convertToResponse);
        return pagedResourcesAssembler.toModel(responses);
    }

    public CollectionModel<EntityModel<PredeterminationLogResponse>> fetchPredeterminationDetail(String claimId ,PageRequest pagable ) {

        Optional<Page<PredeterminationLog>> predeterminationLogs = logRepository.findByClaimId(claimId, pagable);
        if (predeterminationLogs.isEmpty() || predeterminationLogs.get().isEmpty()) {
            throw new DataNotFoundException("No Data found for Predetermination Log Request");
        }

        Page<PredeterminationLogResponse> responses = predeterminationLogs.filter(Page::hasContent).map(page -> page.map(this::convertToResponse)).orElseThrow(DataNotFoundException::new);
        return pagedResourcesAssembler.toModel(responses);
    }

    private PredeterminationLogResponse convertToResponse(PredeterminationLog log) {
        return PredeterminationLogResponse.builder()
                .creationDate(log.getCreationDate())
                .status(log.isStatus())
                .claimId(log.getClaimId())
                .reason(log.getReason())
                .input(log.getInput())
                .id(log.getId())
                .build();
    }

    private void validateDocumentTypes(Predetermination request, List<String> errors) {
        log.info("DocValidation Start document type validation for claimId: {}", request.getClaimId());
        String code = request.getCode();

        BenefitsMaster benefitsMaster = benefitMasterService.fetchByInterventionCode(code);

        List<String> requestDocTypes = request.getDocumentAttachments();

        Set<String> dbDocTypes = benefitMasterService
                .fetchDocumentTypesByBenefitIdAndUse(benefitsMaster.getId()).stream()
                .map(DocumentType::getCode)
                .map(String::trim)
                .collect(Collectors.toSet());

        Set<String> reqDocTypes = requestDocTypes.stream()
                .map(String::trim)
                .collect(Collectors.toSet());

        // Find missing documents (case-insensitive)
        Set<String> missingDocs = dbDocTypes.stream()
                .filter(dbDoc -> reqDocTypes.stream()
                        .noneMatch(reqDoc -> reqDoc.equalsIgnoreCase(dbDoc)))
                .collect(Collectors.toSet());

        if (!missingDocs.isEmpty()) {
            errors.add("Missing required document: " + missingDocs + " has not been submitted.");
        }
    }
}