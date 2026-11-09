package io.coredge.health.claims.service;

import brave.Tracer;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.coredge.health.claims.utils.*;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import io.coredge.health.claims.models.PolicyPeriod;
import io.coredge.health.claims.utils.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.camunda.zeebe.client.ZeebeClient;
import io.coredge.health.claims.component.ClaimBundleComponent;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.*;
import io.coredge.health.claims.db.repositories.*;
import io.coredge.health.claims.dto.facility.BedOccupancyDto;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.enums.IdentifierType;
import io.coredge.health.claims.exception.app.*;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.helpers.ClaimValidationUtility;
import io.coredge.health.claims.helpers.HieHelper;
import io.coredge.health.claims.helpers.PatientHelper;
import io.coredge.health.claims.helpers.ProviderHelper;
import io.coredge.health.claims.models.ClaimUpdateLogs;
import io.coredge.health.claims.models.Predetermination;
import io.coredge.health.claims.redis.RedisMessagePublisher;
import io.coredge.health.claims.service.facility.FacilityBedOccupancyService;
import io.coredge.health.claims.service.intervention.InterventionValidationService;
import io.coredge.health.claims.service.shr.ShrHelper;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.*;
import static io.coredge.health.claims.constants.Constants.SubType.*;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;
import static io.coredge.health.claims.constants.FhirSystemConstants.VALID_CLAIM_SUB_TYPES;
import static io.coredge.health.claims.exception.models.ErrorCode.*;
import static org.apache.jena.sparql.engine.optimizer.reorder.ReorderTransformationSubstitution.log;
import static org.hl7.fhir.r4.model.Claim.Use.CLAIM;

@Slf4j
@Service
public class ClaimBundleService {

    public static final String PRACTITIONER_KEYWORD = "Practitioner/";

    @Value("${preauth.validation.enabled:false}")
    private boolean validationEnabled;

    private final List<ExtensionStateCodeEnum> CLAIM_CALLBACK_STATES = List.of(
            ExtensionStateCodeEnum.APPROVED,
            ExtensionStateCodeEnum.PAYMENT_COMPLETED,
            ExtensionStateCodeEnum.REJECTED,
            ExtensionStateCodeEnum.SENT_BACK,
            ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    @Value("${benefitsRestrictionsEnabled:true}")
    private boolean benefitsRestrictionsEnabled;
    @Value("${preAuthDocReq}")
    private boolean isPreAuthDocReq;
    @Value("${claimDocReq}")
    private boolean isClaimDocReq;
    @Value("${validateMemberWithHie: false}")
    private boolean validateMemberWithHie;
    @Value("${validatePractitionerWithHie: false}")
    private boolean validatePractitionerWithHie;
    @Value("${validateProviderWithHie: false}")
    private boolean validateProviderWithHie;

    @Value("${dnm.extended.validation.enable:false}")
    private boolean validateDnmExtendedEnable;

    @Value("${facility.bed.occupancy.enable:false}")
    private boolean facilityBedOccupancy;

    @Value("${ips.enable.flag:false}")
    private boolean ipsFlag;

    // Interventions that require Pre Auth after 12 days.
    @Value("#{'${interventions.reqPreAuth}'.split(',')}")
    private List<String> interventionsReqPreAuth;

    @Value("${ai.notes.enable:false}")
    private boolean isAiNotesEnable;

    @Value("${finsprint.eligibility.enabled}")
    private boolean finsprintEligibilityEnabled;

    @Value("${enable.redis.idempotency.check}")
    private boolean redisIdempotencyCheckEnabled;

    @Value("${hie.retry.maxAttempts:3}")
    private int hieRetryMaxAttempts;

    @Value("${hie.retry.delayMs:1000}")
    private long hieRetryDelayMs;

    private final IGenericClient fhirClient;
    private final FhirContext fhirContext;
    private final IParser fhirParser;
    private final PostOfficeService postOfficeService;
    private final BenefitMasterService benefitMasterService;
    private final InsuranceClaimsService insuranceClaimsService;
    private final InsuranceClaimsRepository claimsRepository;
    private final WorkflowService wfService;
    private final FhirHelper fhirHelper;
    private final ProviderRepository providerRepository;
    private final ProviderService providerService;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final PractitionerDetailsService practitionerDetailsService;
    private final LineItemRepository lineItemRepository;
    private final ClaimsHandlerUtility claimsHandlerUtility;
    private final HieHelper hieHelper;
    private final ZeebeClient zeebe;
    private final RedisMessagePublisher redisMessagePublisher;
    private final ClaimBundleTransactionalService bts;
    private final ClaimDataUpdateRepository claimDataUpdateRepository;
    private final ClaimsValidator claimsValidator;
    private final PatientHelper patientHelper;
    private final StatusUpdateService statusUpdateService;
    private final ClaimBundleComponent component;
    private final ClaimBundleLogService logService;
    private final ProviderHelper providerHelper;
    private final ShrHelper shrHelper;
    private final MaternityApprovalService maternityApprovalService;
    private final FacilityBedOccupancyService facilityBedOccupancyService;
    private final SurveillanceService surveillanceService;
    private final InterventionDurationRepository interventionDurationRepository;
    private final Tracer tracer;
    private final ClaimPractitionerMappingRepository claimPractitionerMappingRepository;
    private final ReactiveClaimAdjudicationService reactiveClaimAdjudicationService;
    private final InterventionValidationService interventionValidationService;

    public ClaimBundleService(IGenericClient fhirClient, FhirContext fhirContext, IParser fhirParser,
                              PostOfficeService postOfficeService, BenefitMasterService benefitMasterService,
                              InsuranceClaimsService insuranceClaimsService,
                              InsuranceClaimsRepository claimsRepository, WorkflowService wfService,
                              FhirHelper fhirHelper, ProviderRepository providerRepository, ProviderService providerService,
                              MemberRepository memberRepository, MemberService memberService,
                              PractitionerDetailsService practitionerDetailsService, LineItemRepository lineItemRepository,
                              ClaimsHandlerUtility claimsHandlerUtility, HieHelper hieHelper, ZeebeClient zeebe,
                              RedisMessagePublisher redisMessagePublisher, ClaimBundleTransactionalService bts,
                              ClaimDataUpdateRepository claimDataUpdateRepository, ClaimsValidator claimsValidator,
                              PatientHelper patientHelper, StatusUpdateService statusUpdateService,
                              ClaimBundleComponent component, ClaimBundleLogService logService,
                              InterventionValidationService interventionValidationService, ProviderHelper providerHelper,
                              ShrHelper shrHelper, MaternityApprovalService maternityApprovalService,
                              FacilityBedOccupancyService facilityBedOccupancyService, SurveillanceService surveillanceService,
                              InterventionDurationRepository interventionDurationRepository, Tracer tracer,
                              ClaimPractitionerMappingRepository claimPractitionerMappingRepository,
                              ReactiveClaimAdjudicationService reactiveClaimAdjudicationService) {
        this.fhirClient = fhirClient;
        this.fhirContext = fhirContext;
        this.fhirParser = fhirParser;
        this.postOfficeService = postOfficeService;
        this.benefitMasterService = benefitMasterService;
        this.insuranceClaimsService = insuranceClaimsService;
        this.claimsRepository = claimsRepository;
        this.wfService = wfService;
        this.fhirHelper = fhirHelper;
        this.providerRepository = providerRepository;
        this.providerService = providerService;
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.practitionerDetailsService = practitionerDetailsService;
        this.lineItemRepository = lineItemRepository;
        this.claimsHandlerUtility = claimsHandlerUtility;
        this.hieHelper = hieHelper;
        this.zeebe = zeebe;
        this.redisMessagePublisher = redisMessagePublisher;
        this.bts = bts;
        this.claimDataUpdateRepository = claimDataUpdateRepository;
        this.claimsValidator = claimsValidator;
        this.patientHelper = patientHelper;
        this.statusUpdateService = statusUpdateService;
        this.component = component;
        this.logService = logService;
        this.interventionValidationService = interventionValidationService;
        this.providerHelper = providerHelper;
        this.shrHelper = shrHelper;
        this.maternityApprovalService = maternityApprovalService;
        this.facilityBedOccupancyService = facilityBedOccupancyService;
        this.surveillanceService = surveillanceService;
        this.interventionDurationRepository = interventionDurationRepository;
        this.tracer = tracer;
        this.claimPractitionerMappingRepository = claimPractitionerMappingRepository;
        this.reactiveClaimAdjudicationService = reactiveClaimAdjudicationService;
    }


    private FhirValidator validator;

    public static boolean isBeforeCutOffDate(Date billableStartDate, Claim.Use use) {
        LocalDate cutoffDate = LocalDate.of(2024, 11, 7);
        LocalDateTime startDate = FhirHelper.convertToLocalDateTime(billableStartDate);
        LocalDate serviceDate = startDate.toLocalDate();
        return serviceDate.isBefore(cutoffDate);
    }

    /**
     * Initialize things after bean is constructed.
     */
    @PostConstruct
    public void postBeanInit() {
        validator = fhirContext.newValidator();
        //validator.setConcurrentBundleValidation(true);
        // This is only used,if the validation is performed with validationOptions.isConcurrentBundleValidation = true
        //validator.setExecutorService(Executors.newFixedThreadPool(256*4));

        log.warn("Validator isConcurrentBundleValidation: {}", validator.isConcurrentBundleValidation());
        log.warn("Validator isValidateAgainstStandardSchema: {}", validator.isValidateAgainstStandardSchema());
        log.warn("Validator isSkipContainedReferenceValidation: {}", validator.isSkipContainedReferenceValidation());
        log.warn("Validator isValidateAgainstStandardSchematron: {}", validator.isValidateAgainstStandardSchematron());
    }


    // Current Flow
    public Bundle claimJsonMethod(String claimBundlJson) {
        return (Bundle) processRequestBundleInitially(claimBundlJson, false, true);
    }

    // Current Flow
    public Bundle bundleMethod(String bundleJson) {
        return (Bundle) processRequestBundleInitially(bundleJson, false, false);
    }

    // Async Method
    public String bundleMethodAsync(String bundleJson) {
        return (String) processRequestBundleInitially(bundleJson, true, false);
    }

    public Object processRequestBundleInitially(String bundleJson, Boolean isAsync, Boolean isClaimJson) {
        Bundle inputBundle = null;
        Bundle responseBundle;
        Claim claim = null;
        Map<String, String> preAuthShaCodeMap = new HashMap<>();
        try {
            // Step 1: Parse and validate the input inputBundle
            if (isClaimJson) {
                claim = parseAndValidateClaimJson(bundleJson);
            } else {
                inputBundle = parseAndValidateBundle(bundleJson);
            }
            //Note: Ensure that we are not pointing the same inputBundle object but a new inputBundle itself.
            responseBundle = fhirParser.parseResource(Bundle.class, bundleJson);

        } catch (Exception ex) {
            log.warn("Error while parsing input json {}", ex.getMessage());
            throw new BusinessException(BAD_REQUEST, "Invalid input json." + ex);
        }

        // Step 2.1: Handle the existing claim in the system If a duplicate request is received.
        if (!isClaimJson) {
            claim = extractClaim(inputBundle);
        }

        //Note. If claimId is resubmitted, system responds and triggers callbacks immediately, skipping validations.
        // Handle existing claim for duplicate requests.
        String externalClaimId = claim.getIdPart();
        String use = claim.getUse().toString();
        String patientId = FhirHelper.extractPatientId(claim);

        // Set the HealthIdContext for logging/audit purposes.
        HealthIdContextHolder.setClaimContext(externalClaimId, patientId, use);
        // sync into MDC so logs after this line contain them
        MdcUtil.syncWithContextHolder();

        if (insuranceClaimsService.existsByExternalId(externalClaimId)) {
            return handleExistingClaim(externalClaimId, responseBundle);
        }

        assert inputBundle != null;
        MessageHeader requestMessageHeader = extractMessageHeader(inputBundle);

        // Step 3: Validate new claim
        checkDataValidations(claim, inputBundle, requestMessageHeader, preAuthShaCodeMap);

        // [MIGRATED TO PostSaveValidationService] Pre-auth claims validation (EPORTAL-181)
        // Previously checked here, now validated post-save

        //Step 3: After all the FHIR and Custom Validation , Process the Request
        // if async PUBLISH to the subscriber or process as existing flow
        if (isAsync) {
            boolean isEmergency = claim.hasSubType() &&
                    EMERGENCY.equalsIgnoreCase(claim.getSubType().getCoding().getFirst().getCode());
            processRequestViaPublisher(externalClaimId, bundleJson, isEmergency);
            OperationOutcome operationOutcome = new OperationOutcome();
            operationOutcome.addIssue()
                    .setCode(OperationOutcome.IssueType.INFORMATIONAL)
                    .setSeverity(OperationOutcome.IssueSeverity.INFORMATION)
                    .setDetails(new CodeableConcept().setText("Claim/PreAuth with ID " + externalClaimId + " submitted successfully"));
            return fhirParser.encodeResourceToString(operationOutcome);
        }
        return processRequestBundleFinally(new ClaimFhirValidator(claim, inputBundle, responseBundle, requestMessageHeader, externalClaimId, preAuthShaCodeMap));
    }

    public List<String> validatePreAuthClaims(Predetermination data){
        return claimsValidator.validatePreAuthApproved(data);
    }

    // Send request to the publisher Service
    public void processRequestViaPublisher(String externalId, String bundleJson, Boolean isEmergency) {
        redisMessagePublisher.publishMessage(externalId, bundleJson, isEmergency);
    }


    @Timed(description = "ClaimBundle Method")
    public Bundle processRequestBundleFinally(ClaimFhirValidator cfv) {
        Bundle inputBundle = cfv.inputBundle;
        Bundle responseBundle = cfv.responseBundle;
        Claim claim = cfv.claim;
        String externalClaimId = cfv.externalClaimId;
        ClaimResponse claimResponse;
        Map<String, String> preAuthShaCodeMap = cfv.preAuthShaCodeMap;

        // Step 4: Process the all resources
        // Process the member/patient data
        Patient patient = extractPatient(inputBundle, claim);
        Member member = processPatientData(patient, claim);

        //PAT-26 . Age validation for SHA-03-003. Code can be reused for any future age validations
//        validatePatientAge(patient,claim);

        // EPORTAL-397
        // Need to save Bundle and fetch IPS information from shared health repository.
        if (Boolean.TRUE.equals(ipsFlag)) {
            log.info("Fetching IPS/SHR for claim {}", claim.getId());
            shrHelper.extractIpsInformation(responseBundle, patient, claim);
        }
        //Process the practitioner/Doctor/Care-Team
        PractitionerDetails practitionerDetail =  practitionerUtils(inputBundle, claim);
        // CareTeam
        List<PractitionerDetails> practitionerDetails = careTeamUtils(inputBundle, claim);
        // Provider
       Provider provider = providerUtils(inputBundle, claim);
        // Facility
        facilityUtils(inputBundle, claim);
        // Insurer
        insurerUtils(inputBundle, claim);
        // RelatedClaims
        parseRelatedClaims(inputBundle, claim);
        // Coverage
        fetchCoverage(inputBundle, claim, patient);

        LocalDateTime policyStartDate = null;
        LocalDateTime policyEndDate = null;
        PolicyPeriod policyPeriod = null;

        boolean isPhcClaim = bts.isPhcClaim(claim);

        LocalDate billableStartDate = FhirHelper.convertToLocalDate(claim.getBillablePeriod().getStart());
        LocalDate billableEndDate = FhirHelper.convertToLocalDate(claim.getBillablePeriod().getEnd());


   if (finsprintEligibilityEnabled) {
            // No preauth with policy dates found, use Finsprint eligibility check.
            // Errors here are caught and logged — the claim still saves.
            // PostSaveValidationService will re-validate and set SENT_BACK if eligibility fails.
            try {
                if (isPhcClaim) {
                    // PHC claims use UHC scheme
                    policyPeriod = bts.validatePhcEligibilityWithFinsprint(member, externalClaimId, billableStartDate, billableEndDate);
                } else {
                    // Non-PHC claims use TSC > POMSF > SHIF priority
                    policyPeriod = bts.validateEligibilityWithFinsprint(member, externalClaimId, billableStartDate, billableEndDate);
                }
                if (policyPeriod.getStartDate() != null && policyPeriod.getEndDate() != null) {
                    policyStartDate = policyPeriod.getStartDate().atStartOfDay();
                    policyEndDate = policyPeriod.getEndDate().atStartOfDay();
                }
            } catch (Exception e) {
                log.warn("Finsprint eligibility check failed for claim {} — claim will save and be validated post-save. Error: {}",
                        externalClaimId, e.getMessage());
                // policyPeriod remains null; post-save validation will catch this
            }
        }

        // DNM extended rule check (EPORTAL-371).
        // Errors here are caught and logged — the claim still saves.
        // PostSaveValidationService will re-validate and set SENT_BACK if rules are violated.
        if (Boolean.TRUE.equals(validateDnmExtendedEnable)) {
            try {
                interventionValidationService.fetchRulesAndValidate(claim, member, provider.getLevel(), policyStartDate, policyEndDate);
            } catch (Exception e) {
                log.warn("Rule validation failed for claim {} — claim will save and be validated post-save. Error: {}",
                        externalClaimId, e.getMessage());
            }
        }

        // Support-70. Bed Occupancy
        if (facilityBedOccupancy) {
            facilityBedOccupancyService.updateBedOccupancyForClaimItem(claim, provider);
        }

        // Step 5: Submit the Resource. Process and Save to FHIR/DB
        ClaimDetailResponce claimDetailResponce = bts.processClaimRequest(claim, inputBundle, responseBundle, cfv.requestMessageHeader,
                externalClaimId, provider, member, preAuthShaCodeMap, policyPeriod);

        claimResponse = claimDetailResponce.claimResponse();
        InsuranceClaim iClaim = claimDetailResponce.claim();

        //Save mapping of Practitioner and InsuranceClaim in Database
        if (Objects.nonNull(iClaim)) {
            List<ClaimPractitionerMapping> claimPractitionerMapping = new ArrayList<>();
            Optional.ofNullable(practitionerDetail).ifPresent(practitioner ->
                    claimPractitionerMapping.add(new ClaimPractitionerMapping(iClaim, practitionerDetail, "Practitioner" )));

            Optional.ofNullable(practitionerDetails)
                    .filter(list -> !list.isEmpty())
                    .ifPresent(details ->
                            details.forEach(detail ->
                                    claimPractitionerMapping.add(new ClaimPractitionerMapping(iClaim, detail ,"Care_Team" ))
                            )
                    );
            claimPractitionerMappingRepository.saveAll(claimPractitionerMapping);
        }

        //Step 6: Send the Callbacks
        statusUpdateService.triggerCallBackStatusUpdate(claim.getIdPart(), true,true, false);

        triggerAutoAdjudication(claim, claimResponse);

        return responseBundle;
    }

    public record ClaimDetailResponce(InsuranceClaim claim,ClaimResponse claimResponse) {
    }


    public record ClaimFhirValidator(Claim claim, Bundle inputBundle, Bundle responseBundle,
                                     MessageHeader requestMessageHeader, String externalClaimId,
                                     Map<String, String> preAuthShaCodeMap) {
    }

    /**
     * Triggers the auto adjudication process for the given claim.
     *
     * @param claim         the claim object containing details of the insurance claim to be processed
     * @param claimResponse the response associated with the claim, providing additional information for processing
     */
    private void triggerAutoAdjudication(Claim claim, ClaimResponse claimResponse) {
        String traceId = tracer.currentSpan() != null ? tracer.currentSpan().context().traceIdString() : UUID.randomUUID().toString();
        Map<String, Object> workflowParams = new HashMap<>();
        workflowParams.put(VAR_CLAIM_ID, claim.getIdPart());
        workflowParams.put(VAR_CLAIM_RESPONSE_ID, claimResponse.getIdPart());
        workflowParams.put(VAR_CLAIM_TYPE, claim.getUse());
        workflowParams.put(VAR_TRACE_ID, traceId);
        if (redisIdempotencyCheckEnabled) {
            reactiveClaimAdjudicationService.triggerAutoAdjudication(claim.getUse(), workflowParams);
        } else {
            wfService.startBusinessProcess(claim.getUse(), workflowParams);
        }
    }

    private @NotNull Bundle handleExistingClaim(String externalClaimId, Bundle responseBundle) {
        ClaimResponse claimResponse;
        InsuranceClaim iClaim = claimsRepository.findByExternalId(externalClaimId).flatMap(list -> list.stream().findFirst()).orElse(null);;
        assert iClaim != null;
        log.info("Claim with ExternalId: '{}' already exists️, Status '{}'. "
                + "proceeding with callbacks as its already processed.", externalClaimId, iClaim.getStatus().name());

        claimResponse = postOfficeService.getClaimResponseByClaimId(externalClaimId, iClaim.getClaimResponseId());
        if (claimResponse == null) {
            log.info("ClaimResponse is not found against External-ID '{}'", externalClaimId);
            throw new RuntimeException("Error occurred, ClaimResponse is not found against claim ID :" + externalClaimId);
        }
        responseBundle.addEntry(new Bundle.BundleEntryComponent().setResource(claimResponse));
        // Note: callbacks should be sent for only finished/processed claims. Queued claims should be triggered, etc.
        if (CLAIM_CALLBACK_STATES.contains(iClaim.getStatus())) {
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, externalClaimId, iClaim.getStatus());
        }
        return responseBundle;
    }


    private Member processPatientData(Patient patient, Claim claim) {
        Member member;
        if (validateMemberWithHie) {
            member = validatePatientWithHieServer(patient, claim);
        } else {
            member = fetchOrCreateMember(patient);
        }
        if (patient != null) {
            fhirClient.update().resource(patient).execute();
        }
        return member;
    }


    private @NotNull Member fetchOrCreateMember(Patient patient) {
        // Process the Member Details
        String patientId = patient != null ? patient.getIdPart() : "";
        if (patientId.contains("/")) {
            patientId = patientId.split("Patient/")[1];
        }
        return memberRepository.existsByCrId(patientId).orElse(false) ?
                updateMemberIfNeeded(memberRepository.findByCrId(patientId).get().getFirst(), patient) :
                memberRepository.save(Member.fromFhirPatient(null, patient));
    }

    private Member updateMemberIfNeeded(Member member, Patient patient) {
        boolean updated = (member.getHouseholdNumber() == null || member.getHouseholdNumber().isEmpty()) ||
                (member.getPhoneNumber() == null || member.getPhoneNumber().isEmpty()) ||
                (member.getShaNumber() == null || member.getShaNumber().isEmpty());
        if (updated) {
            member = Member.fromFhirPatient(member, patient);
            memberRepository.save(member);
        }
        return member;
    }

    /**
     * Validates a patient by accessing the HIE (Health Information Exchange) server using the given patient ID,
     * fetches patient data, and updates or saves it to the database and optionally to the FHIR (Fast Healthcare Interoperability Resources) server.
     * If the patient data is not found, an exception is thrown.
     *
     * @param patientProvider The Patient object from the provider system to be validated and fetched from the HIE system.
     * @param claim          The Claim object associated with the patient, used for logging and error handling.
     * @return A Member object representing the patient after validation and persistence.
     * @throws RuntimeException If there is an issue while accessing the HIE server or performing database/FHIR operations.
     */
    private Member validatePatientWithHieServer(Patient patientProvider, Claim claim) {
        try {
            String patientId = patientProvider.getIdPart();

            // 1. Validate/fetch Patient From HIE with retry mechanism
            log.info("Validating Beneficiary from HIE, ID :  {}", patientId);
            String patientJson = executeWithHieRetry(
                    () -> hieHelper.getPatientFromHieViaShaNumber(patientId),
                    "validateMemberWithHie for patientId: " + patientId
            );
            Patient patientHie = validatePatientHie(patientProvider, patientId, patientJson);

            // 2. Save/update to DB/FHIR
            return saveDataPatient(patientJson, patientHie, patientId);
        } catch (RuntimeException e) {
            log.warn("Error while processing Beneficiary for claim ID {}. Cause: {}", claim.getId(), e.getMessage(), e);
            throw new RuntimeException("Error while processing Beneficiary for claim ID %s. Cause: %s".formatted(claim.getId(), e.getMessage()));
        }
    }

    public List<String> validatePatientWithHieServer(Predetermination data ,Patient patientHie) {
            log.info("Validating Beneficiary from HIE, ID :  {}", data.getPatientId());
            return validatePatientHie(data , patientHie);
    }

    public Patient getPatientWithHieServer(Predetermination data ,String identifier) {
        log.info("Validating Beneficiary from HIE, ID :  {}", data.getPatientId());
        String patientJson = hieHelper.getPatientFromHie(data.getPatientId() , identifier);
        if (StringUtils.isBlank(patientJson))
            throw new DataValidationException(String.format("Beneficiary records not found in HIE, Reference ID : %s", data.getPatientId()));
         return fhirContext.newJsonParser().parseResource(Patient.class, patientJson);
    }

    private Member saveDataPatient(String patientJson, Patient patientHie, String patientId) {
        Member member = memberService.saveToDatabase(Member.fromJsonBundle(patientJson));
        log.info("Patient : {}  in FHIR. Creating/updating a entry...", patientId);
        MethodOutcome outcome = fhirClient.update().resource(patientHie).execute();
        log.info("Created/updated Patient resource ID: {}", outcome.getId());
        return member;
    }

    private Patient validatePatientHie(Patient patientProvider, String patientId, String patientJson) {
        if (StringUtils.isBlank(patientJson)) {
            log.warn("Beneficiary records not found in HIE, Reference ID : {}", patientId);
            throw new PatientNotFoundException(patientId);
        } else {
            // Parse patient JSON, save to DB, and store in FHIR if needed
            Patient patientHie = fhirContext.newJsonParser().parseResource(Patient.class, patientJson);
            // Validate patient data.
            patientHelper.validatePatientData(patientProvider, patientHie);
            return patientHie;
        }
    }

    private List<String> validatePatientHie(Predetermination data , Patient patientHie) {
        List<String> errors = new ArrayList<>();
            // Validate patient data.
            Optional.ofNullable(patientHelper.validatePatientGender( data.getPatientId(), data.getGender() , patientHie)).ifPresent(errors::add);
            Optional.ofNullable(patientHelper.validatePatientBirthDate(data.getPatientId() , data.getBirthDate(), patientHie.getBirthDate())).ifPresent(errors::add);
            Optional.ofNullable(patientHelper.validatePatientHouseHoldNumber(data.getPatientId(), data.getHouseholdNumber(), patientHie)).ifPresent(errors::add);
        return errors;
    }


    /**
     * VALIDATING PRACTITIONER
     * Validates a Practitioner by accessing the HIE (Health Information Exchange) server using the given Practitioner ID,
     * fetches practitioner data, and updates or saves it to the database and optionally to the FHIR (Fast Healthcare Interoperability Resources) server.
     * If the practitioner data is not found, an exception is thrown.
     *
     * @param practitionerRegNo The unique ID of the practitioner to be validated and fetched from the HIE system.
     * @return A Member object representing the practitioner after validation and persistence.
     * @throws RuntimeException If there is an issue while accessing the HIE server or performing database/FHIR operations.
     */
    private Practitioner validatePractitionerWithHieServer(String practitionerRegNo) {
        Practitioner practitioner;
        try {
            // 1. Validate/fetch Practitioner From HIE with retry mechanism
            // 2. Save/update to DB/FHIR
            log.info("Validating Practitioner from HIE, ID :  {}", practitionerRegNo);
            String practitionerJson = executeWithHieRetry(
                    () -> hieHelper.getPractitionerFromHieViaRegNo(practitionerRegNo),
                    "validatePractitionerWithHie for practitionerRegNo: " + practitionerRegNo
            );
            if (StringUtils.isNotEmpty(practitionerJson)) {
                // Parse patient JSON, save to DB, and store in FHIR if needed
                   practitioner = fhirContext.newJsonParser().parseResource(Practitioner.class, practitionerJson);
                   fhirClient.update().resource(practitioner).execute(); // Update if found
            } else {
                log.warn("Practitioner records not found in HIE, Reference ID : {}", practitionerRegNo);
                throw new DataValidationException(String.format("Invalid Practitioner: Data for Practitioner with " +
                        "practitionerId [%s] not found in the HIE system.", practitionerRegNo));
            }
        } catch (DataValidationException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.warn("An exception occurred while accessing the HIE service. Error message: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return practitioner;
    }

    private Bundle parseAndValidateBundle(String bundleJson) {
        Bundle bundle = fhirParser.parseResource(Bundle.class, bundleJson);
        ValidationResult result = validator.validateWithResult(bundle);
        if (!result.isSuccessful()) {
            // If validation fails, return the validation errors
            // TODO: at some point we will have to be strict about these validations.
            IBaseOperationOutcome operationOutcome = result.toOperationOutcome();
            log.warn("Validation errors in bundle: {}", result.getMessages().toString());
        }
        return bundle;
    }

    private Claim parseAndValidateClaimJson(String claimJson) {
        Claim claim = fhirParser.parseResource(Claim.class, claimJson);
        ValidationResult result = validator.validateWithResult(claim);
        if (!result.isSuccessful()) {
            // If validation fails, return the validation errors
            // TODO: at some point we will have to be strict about these validations.
            IBaseOperationOutcome operationOutcome = result.toOperationOutcome();
            log.warn("Validation errors in claimJson: {}", result.getMessages().toString());
        }
        return claim;
    }

    private Claim extractClaim(Bundle bundle) {
        for (Iterator<Bundle.BundleEntryComponent> it = bundle.getEntry().iterator(); it.hasNext(); ) {
            Bundle.BundleEntryComponent entry = it.next();
            if (entry.getResource() instanceof Claim) {
                it.remove(); // Remove the Claim resource from the bundle
                return (Claim) entry.getResource();
            }
        }
        throw new BusinessException(BAD_REQUEST, "Claim resource not found in the bundle.");
    }

    private List<Coverage> extractCoverage(Bundle bundle) {
        List<Coverage> coverages = new ArrayList<>();
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() instanceof Coverage) {
//               it.remove(); // Remove the Claim resource from the bundle
                coverages.add((Coverage) entry.getResource());
            }
        }
        return coverages;
    }

    private MessageHeader extractMessageHeader(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() instanceof MessageHeader) {
                return (MessageHeader) entry.getResource();
            }
        }
        return null; // Return null if no MessageHeader is found
    }

    private Patient extractCoverageAndGetPatient(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() instanceof Coverage coverage) {
                Reference beneficiaryReference = coverage.getBeneficiary();
                if (beneficiaryReference != null) {
                    return postOfficeService.getBeneficiaryPatient(bundle, beneficiaryReference.getReference());
                }
            }
        }
        return null; // Return null if no Coverage or Patient is found
    }

    private Organization extractCoverageAndGetProvider(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() instanceof Coverage coverage) {
                Reference beneficiaryReference = coverage.getBeneficiary();
                if (beneficiaryReference != null) {
                    return postOfficeService.getBeneficiary(bundle, beneficiaryReference.getReference());
                }
            }
        }
        return null; // Return null if no Provider is found
    }


    private Patient extractPatient(Bundle bundle, Claim claim) {
        Patient patient = extractCoverageAndGetPatient(bundle);
        if (patient == null) {
            // Fallback: Use utility method to process patient from the claim
            patient = patientHelper.getPatient(bundle, claim);
        }
        return patient;
    }

    private Provider providerUtils(Bundle bundle, Claim claim) {
        try {
            Organization organization = extractProvider(bundle, claim);
            validateOrganization(organization);

            Provider provider;
            // Validate the provider with HIE only if enabled
            if (validateProviderWithHie) {
                provider = validateProviderWithHieServer(organization);
            } else {
                provider = providerUtils(organization, claim.getIdPart());
            }

            validateSha01ClaimEligibility(provider , claim);

            // Add org in the claim.
            updateClaimWithProviderContained(claim, organization);
            return provider;
        } catch (RuntimeException e) {
            log.warn("Error while processing Organization for claim ID {}. Cause: {}", claim.getId(), e.getMessage(), e);
            throw new RuntimeException("Error while processing Organization for claim ID %s. Cause: %s".formatted(claim.getId(), e.getMessage()));
        }
    }

    private void validateSha01ClaimEligibility(Provider provider, Claim claim) {
        List<String> codes = claimsValidator.extractInterventionCodes(claim);
        if (provider != null && claimsValidator.checkCodeExist(codes, SHA_01_INTERVENTION_CODE)){
            if (!GOVERNMENT_FACILITIES_TYPES.isEmpty() &&
                    GOVERNMENT_FACILITIES_TYPES.stream()
                            .noneMatch(type -> type.equalsIgnoreCase(provider.getFacilityAgent().trim()))){
                throw new DataValidationException(BAD_REQUEST, "Provider not Allowed for submitting claim of SHA-01, since It is not a Government Facility");
            }
            if(!FACILITY_LEVEL_ALLOWED_FOR_SHA_01.isEmpty() &&
                    FACILITY_LEVEL_ALLOWED_FOR_SHA_01.stream()
                            .noneMatch(level -> provider.getLevel().toLowerCase().trim().contains(level.toLowerCase()))){
                throw new DataValidationException(BAD_REQUEST, "Provider not Allowed for submitting claim of SHA-01 , Facility Level is not Authorized to Submit Claim");
            }
        }
    }

    private @NotNull Organization extractProvider(Bundle bundle, Claim claim) {
        Organization organization = extractCoverageAndGetProvider(bundle);
        if (organization == null) {
            // Fallback: Use utility method to process Provider from the claim
            String reference = claim.getProvider().getReference();
            organization = (Organization) postOfficeService.getResourceByUrl(bundle, reference, claim.getProvider().getId());
            if (organization == null) {
                throw new RuntimeException("Referenced Provider: " + reference + " is not found in bundle.");
            }
        }
        return organization;
    }

    private void validateOrganization(Organization organization) {
//        String orgId = organization.getIdElement() != null ? organization.getIdElement().getIdPart() : null;

//        // Valid the Org , Only FR Code is Supported
//        if (StringUtils.isEmpty(orgId) || !orgId.startsWith("FID-")) {
//            throw new RuntimeException(
//                    "Invalid Organization ID. Expected format: 'FID-XXXX-X' (Facility Registration Code). " +
//                            "Provided ID: %s".formatted(orgId)
//            );
//        }

        String facilityLevel = providerHelper.getFacilityLevel(organization, "facility-level");
        if (StringUtils.isEmpty(facilityLevel)) {
            throw new FacilityLevelNotFoundException("Provider : " + organization.getIdElement().getIdPart());
        }
    }


    /**
     * Method to validate basic business validations for request claim bundle.
     *
     * @param claim                claim bundle
     * @param bundle               Bundle
     * @param requestMessageHeader request header
     * @param preAuthShaCodeMap
     * @throws DataValidationException if there is any data issue.
     */
    private void checkDataValidations(Claim claim, Bundle bundle, MessageHeader requestMessageHeader, Map<String, String> preAuthShaCodeMap) {

        // Validate if the claim exists and has an ID
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (claim == null || claim.getId() == null) {
            throw new DataValidationException(MISSING_CLAIM_IN_BUNDLE, "No Claim resource found in the Bundle.");
        }

        // Validate BillablePeriod (start and end dates)
        if (!claim.hasBillablePeriod() || !claim.getBillablePeriod().hasStart() || !claim.getBillablePeriod().hasEnd()) {
            throw new DataValidationException("Billable Period can't be blank");
        }

        // Validate request event coding
        if (requestMessageHeader != null && !requestMessageHeader.getEventCoding().getCode().equals("claim-request")) {
            throw new DataValidationException("Invalid EventCoding in MessageBundle.");
        }

        if (requestMessageHeader != null && !requestMessageHeader.getEventCoding().getCode().equals("claim-request")) {
            throw new RuntimeException("Invalid EventCoding in MessageBundle.");
        }

        // [MIGRATED TO PostSaveValidationService] Attachment requirement check
        // Previously checked here, now validated post-save

        // Validate for null Sha Code i.e. Intervention Code in Claim Object
        if (ClaimValidationUtility.hasNullProductOrServiceCode(claim)) {
            throw new DataValidationException(NO_INTERVENTION_FOUND, "Please provide at least 1 intervention code in productOrService.");
        }

        // [MIGRATED TO PostSaveValidationService] Intervention code validity check
        // Previously checked here, now validated post-save

        // [MIGRATED TO PostSaveValidationService] Claim sub-type validation
        // Previously checked here, now validated post-save

        // [MIGRATED TO PostSaveValidationService] Claim total amount check
        // Previously checked here, now validated post-save

        // Validate that provider reference is present in the claim
        if (!claim.hasProvider()) {
            throw new DataValidationException(MISSING_PROVIDER_REF, "Missing provider reference in claim.");
        }

        // Validate insurance coverage references for SHA and PMF
        if (claim.hasInsurance()) {
            boolean shaCoverageItem = claim.getItem().stream().anyMatch(
                    item -> item.getExtension().stream().anyMatch(extension -> extension.getUrl().contains("sha")));
            boolean pmfCoverageItem = claim.getItem().stream().anyMatch(
                    item -> item.getExtension().stream().anyMatch(extension -> extension.getUrl().contains("pmf")));

            boolean shaCoverage = claim.getInsurance().stream().anyMatch(
                    insurance -> insurance.getCoverage().getReference().contains("sha"));
            boolean pmfCoverage = claim.getInsurance().stream().anyMatch(
                    insurance -> insurance.getCoverage().getReference().contains("pmf"));

            if (shaCoverageItem && !shaCoverage) {
                throw new IllegalArgumentException("SHA Coverage reference match not found.");
            }

            if (pmfCoverageItem && !pmfCoverage) {
                throw new IllegalArgumentException("PMF Coverage reference match not found.");
            }
        }

        if(claim.hasItem()){
            // SHA code format validation (PREFIX-NUM-NUM) remains here as structural
            validateSHACodeFormat(claim);
            // Extract pre-auth IDs from related entries and map them to intervention codes.
            // No validation is done here — errors surface post-save in PostSaveValidationService (SENT_BACK).
            // This ensures preAuthId is set on ClaimLineItems so hasPreAuth is correctly populated.
            preAuthShaCodeMap.putAll(claimsValidator.extractPreAuthShaCodeMap(claim));
        }
        // [MIGRATED TO PostSaveValidationService] Pre-auth required check
        // Previously checked here, now validated post-save
    }

    /**
     * Validates the SHA code in the claim.
     * Throws an exception if the serviceStart date and end date are valid for per-diem items.
     * @param claim The claim to validate.
     */
    public void validatePerDiemInterventionsAndBillablePeriod(Claim claim, SimpleDateFormat sdf) {
        List<Claim.ItemComponent> sortedItems = new ArrayList<>(claim.getItem());
        sortedItems.sort(Comparator.comparingInt(Claim.ItemComponent::getSequence));

        if ((!sortedItems.getFirst().hasServicedPeriod() || !sortedItems.getLast().hasServicedPeriod())
                || sortedItems.getFirst().hasServicedDateType()) {
            throw new DataValidationException("INVALID_BILLABLE_PERIOD : Claim items require servicedPeriod for validation.");
        }

        List<Claim.ItemComponent> losItems = new ArrayList<>();
        List<Claim.ItemComponent> nonLosItems = new ArrayList<>();

        // Separate LOS and non-LOS items
        for (Claim.ItemComponent item : sortedItems) {
            Date startDate = item.getServicedPeriod().getStart();
            Date endDate = item.getServicedPeriod().getEnd();

            if (!sdf.format(startDate).equals(sdf.format(endDate))) {
                losItems.add(item); // LOS
            } else {
                nonLosItems.add(item); // Non-LOS
            }
        }

        // Validate LOS items
        losItems.sort(Comparator.comparingInt(Claim.ItemComponent::getSequence));
        Date previousEndDate = null;
        String previousCode = null;

        LocalDateTime claimStartDate = FhirHelper.convertToLocalDateTime(claim.getBillablePeriod().getStart());

        for (Claim.ItemComponent item : losItems) {
            String interventionCode = item.getProductOrService().getCoding().getFirst().getCode();
            boolean byPassForPHC = interventionCode.contains("SHA-12") || interventionCode.contains("PMF-12");
            if (byPassForPHC) {
                continue;
            }
            int seqNo = item.getSequence();

            Date startDate = item.getServicedPeriod().getStart();
            Date endDate = item.getServicedPeriod().getEnd();

            checkValidBillablePeriod(claim, startDate, endDate, sdf);

            if (previousEndDate != null && startDate.before(previousEndDate)) {

                List<InterventionDuration> dbInterventionDuration = interventionDurationRepository.findCodeThatSubStringOf(previousCode);
                if (!dbInterventionDuration.isEmpty()) {
                    boolean isAllowed = dbInterventionDuration.stream()
                            .filter(duration -> Objects.isNull(duration.getEffectiveDate()) || duration.getEffectiveDate().isBefore(claimStartDate))
                            .map(InterventionDuration::getAllowedCombination)
                            .filter(Objects::nonNull)
                            .flatMap(Collection::stream)
                            .filter(Objects::nonNull)
                            .anyMatch(code -> interventionCode.toLowerCase().contains(code.toLowerCase()));

                    boolean unallowed = dbInterventionDuration.stream()
                            .filter(duration -> Objects.isNull(duration.getEffectiveDate()) || duration.getEffectiveDate().isBefore(claimStartDate))
                            .map(InterventionDuration::getUnallowedCombination)
                            .filter(Objects::nonNull)
                            .flatMap(Collection::stream)
                            .filter(Objects::nonNull)
                            .anyMatch(code -> interventionCode.toLowerCase().contains(code.toLowerCase()));

                    // UnAllowed SHA codes against Primary code
                    if(isAllowed && !unallowed){
                        continue;
                    }
                }

                throw new BusinessException(INVALID_INTERVENTION_SEQUENCE,
                        "LOS Intervention sequence " + seqNo + " must start on or after the previous LOS sequence ended. Found start: "
                                + sdf.format(startDate) + ", previous end: " + sdf.format(previousEndDate));

            }

                previousEndDate = endDate;
                previousCode = interventionCode;

                if (claim.getUse().equals(CLAIM)) {
                    validatePreAuthForLongStay(claim, interventionCode, startDate, endDate);
                }

        }

        // Non-LOS items: only check valid billable period
        for (Claim.ItemComponent item : nonLosItems) {
            Date startDate = item.getServicedPeriod().getStart();
            Date endDate = item.getServicedPeriod().getEnd();
            checkValidBillablePeriod(claim, startDate, endDate, sdf);
        }

        // Validate overall billablePeriod only if all are LOS items
        boolean allItemsLOS = sortedItems.stream().allMatch(item -> {
            Date start = item.getServicedPeriod().getStart();
            Date end = item.getServicedPeriod().getEnd();
            return !sdf.format(start).equals(sdf.format(end));
        });

        Date billableStartDate = claim.getBillablePeriod().getStart();
        Date billableEndDate = claim.getBillablePeriod().getEnd();

        // Find the earliest start date and latest end date from all items
        Date servicedStart = sortedItems.stream()
                .map(item -> item.getServicedPeriod().getStart())
                .min(Date::compareTo)
                .orElse(null);

        Date servicedEnd = sortedItems.stream()
                .map(item -> item.getServicedPeriod().getEnd())
                .max(Date::compareTo)
                .orElse(null);

        if (servicedStart == null || servicedEnd == null) {
            throw new DataValidationException("INVALID_BILLABLE_PERIOD : Unable to determine serviced period from claim items.");
        }

        if (!isSameDay(billableStartDate, servicedStart) || !isSameDay(billableEndDate, servicedEnd)) {
            throw new DataValidationException("INVALID_BILLABLE_PERIOD : Claim billablePeriod must match servicedPeriod of LOS items. Expected start: "
                    + sdf.format(billableStartDate) + ", end: " + sdf.format(billableEndDate) + ". Found: start: "
                    + sdf.format(servicedStart) + ", end: " + sdf.format(servicedEnd));
        }

    }

    private boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private void checkValidBillablePeriod(Claim claim, Date billableStartDate, Date billableEndDate, SimpleDateFormat sdf) {

        String billableStartDateFmt = sdf.format(billableStartDate);
        String billableEndDateFmt = sdf.format(billableEndDate);
        if (billableStartDate.after(billableEndDate)) {
            throw new DataValidationException("The billing start date : " + billableStartDateFmt + " cannot be after billing end date : " + billableEndDateFmt + " for Claim ID: " + claim.getIdPart());
        }

        // Only compare dates, not times
        LocalDate billableEndLocalDate = FhirHelper.convertToLocalDate(billableEndDate);
        LocalDate today = io.coredge.health.claims.utils.DateUtils.nowNairobiLocalDate();

        if (claim.getUse().equals(CLAIM) && billableEndLocalDate.isAfter(today)) {
            throw new DataValidationException("Billable Period End date cannot be in the future: " + billableEndDateFmt + " for Claim ID: " + claim.getIdPart());
        }

        // Validate billable start date is not before SHA BENEFIT PACKAGE start date "01/10/2024"
        try {
            Date shaServiceStartDate = sdf.parse("2024-10-01");
            if (billableStartDate.before(shaServiceStartDate)) {
                throw new DataValidationException("SHA BENEFIT PACKAGE start date cannot be before 01/10/2024 for Claim ID: " + claim.getIdPart());
            }
        } catch (ParseException e) {
            log.error("Error parsing date: {}", e.getMessage());
            throw new DataValidationException("Invalid date format in Billable Start Period. " + e.getMessage());
        }
    }

    /**
     * Validates whether pre-authorization is required for long-stay i.e. longer than 12 days claims.
     * @param claim
     * @param code
     */
    private void validatePreAuthForLongStay(Claim claim, String code, Date startDate, Date endDate) {
        int los = FhirHelper.calculateLoS(startDate, endDate);
        boolean requiresPreAuth = interventionsReqPreAuth.stream().anyMatch(code::contains) && los > 12;
        // Skip validation for relaxed SHA codes
        // EPORTAL-1499
        if (interventionsReqPreAuth.contains(code)) {
            return;
        }
        if (!requiresPreAuth) return;

        if (!claim.hasRelated()) {
            throw new RuntimeException(String.format("Require Pre Auth not found after 12 days rule. Input LOS: %d, SHA code: %s, Claim ID: %s",
                    los, code, claim.getIdPart()));
        }
        for (Claim.RelatedClaimComponent relatedClaim : claim.getRelated()) {
            Reference claimReference = relatedClaim.getClaim();
            String relatedClaimId = Optional.ofNullable(claimReference.getId())
                    .filter(id -> !id.isBlank())
                    .orElseGet(() -> claimReference.getIdentifier() != null
                            ? claimReference.getIdentifier().getValue()
                            : null);

            if (relatedClaimId == null || relatedClaimId.isBlank()) {
                throw new RuntimeException("Unable to find Related Claim ID from reference.");
            }

            Claim relatedClaimObj = postOfficeService.findClaimById(relatedClaimId);
            if (relatedClaimObj == null) {
                throw new RuntimeException("Referenced Claim: " + relatedClaimId + " is not found in system. Please check");
            }

            findValidApprovedPreAuth(relatedClaimId);
        }
    }

    /**
     * Validates whether pre-authorization is required for long-stay i.e. longer than 12 days claims.
     * @param claim
     * @param code
     */
    private void validatePreAuthForLongStay(Claim claim, String code) {
        int los = FhirHelper.calculateLoS(claim);
        boolean requiresPreAuth = interventionsReqPreAuth.stream().anyMatch(code::contains) && los > 12;
        // Skip validation for relaxed SHA codes
        // EPORTAL-1499
        if (interventionsReqPreAuth.contains(code)) {
            return;
        }

        if (!requiresPreAuth) return;

        if (!claim.hasRelated()) {
            throw new RuntimeException(String.format("Require Pre Auth not found after 12 days rule. Input LOS: %d, SHA code: %s, Claim ID: %s",
                    los, code, claim.getIdPart()));
        }
        for (Claim.RelatedClaimComponent relatedClaim : claim.getRelated()) {
            Reference claimReference = relatedClaim.getClaim();
            String relatedClaimId = Optional.ofNullable(claimReference.getId())
                    .filter(id -> !id.isBlank())
                    .orElseGet(() -> claimReference.getIdentifier() != null
                            ? claimReference.getIdentifier().getValue()
                            : null);

            if (relatedClaimId == null || relatedClaimId.isBlank()) {
                throw new RuntimeException("Unable to find Related Claim ID from reference.");
            }

            Claim relatedClaimObj = postOfficeService.findClaimById(relatedClaimId);
            if (relatedClaimObj == null) {
                throw new RuntimeException("Referenced Claim: " + relatedClaimId + " is not found in system. Please check");
            }

            findValidApprovedPreAuth(relatedClaimId);
        }
    }

    /**
     * Validates whether the total net value of all claim items matches the claim's total amount.
     * <p>
     * This method calculates the total net value by summing up each item's net value
     * multiplied by its quantity. If the computed total does not match the claim's
     * total amount, a {@link DataValidationException} is thrown.
     * </p>
     *
     * @param claim The {@link Claim} object containing claim items and total amount.
     * @throws DataValidationException if the computed total does not match the claim's total amount.
     */
    public void claimTotalCheck(Claim claim) {
        BigDecimal calculatedTotal = claim.getItem().stream()
                .map(item -> item.getNet().getValue().multiply(item.getQuantity().getValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal claimTotal = claim.getTotal().getValue(); // Assuming claim has a getTotal() method

        if (calculatedTotal.compareTo(claimTotal) != 0) {
            throw new DataValidationException(
                    INVALID_CLAIM_TOTAL_AMOUNT,
                    String.format("The total net value of all claim items %s does not match the claim's total amount %s.",
                            calculatedTotal, claimTotal)
            );
        }
    }


    private List<PractitionerDetails> careTeamUtils(Bundle bundle, Claim claim) {
        List<PractitionerDetails> practitionerDetails = new ArrayList<>();
        if (claim.hasCareTeam()) {
            String reference;
            if (claim.hasCareTeam() && claim.getCareTeam().getFirst().hasProvider()) {
                for (Claim.CareTeamComponent careTeam : claim.getCareTeam()) {
                    reference = careTeam.getProvider().getReference();
                    Practitioner practitioner =
                            (Practitioner)
                                    postOfficeService.getResourceByUrl(
                                            bundle, reference, claim.getCareTeam().getFirst().getProvider().getId());
                    if (validatePractitionerWithHie) {
                        Practitioner practitionerHIE = validatePractitionerWithHieServer(practitioner.getIdentifier().getFirst().getValue());
                        claimsValidator.validatePractitioner(practitioner , practitionerHIE);
                        fhirClient.update().resource(practitioner).execute();
                    } else {
                        fhirClient.update().resource(practitioner).execute();
                    }
                    if (practitioner != null) {
                        careTeam.setProvider(new Reference("Practitioner/" + practitioner.getIdPart()));
                        careTeam.getProvider().setDisplay(practitioner.getName().getFirst().getText());
                        claim.addContained(practitioner);
                        PractitionerDetails practitionerDetail = practitionerDetailsService.saveToDatabase(Objects.requireNonNull(PractitionerDetails.fromPractitioner(practitioner)));
                        practitionerDetails.add(practitionerDetail);
                    }
                    }

                }
            }
        return practitionerDetails;
        }

    private void parseRelatedClaims(Bundle bundle, Claim claim) {
        if (claim.getUse().equals(CLAIM) && claim.hasRelated()) {
            List<Claim.RelatedClaimComponent> relatedClaims = claim.getRelated();
            for (Claim.RelatedClaimComponent relatedClaim : relatedClaims) {
                // Get the reference to the related claim
                Reference claimReference = relatedClaim.getClaim();
                String relatedClaimId = claimReference.getId();
                if (StringUtils.isEmpty(relatedClaimId)) {
                    relatedClaimId = claimReference.getIdentifier().getValue();
                }
                Claim relatedClaimObj = postOfficeService.findClaimById(relatedClaimId);
                if (relatedClaimObj == null) {
                    throw new RuntimeException("Referenced Claim: " + relatedClaimId + " is not found in system. Please check");
                }
                findValidApprovedPreAuth(relatedClaimId);
                // Get the relationship type (if available)
                CodeableConcept relationship = relatedClaim.getRelationship();
                String relationshipCode = "pre-auth";
                if (relationship != null && !relationship.getCoding().isEmpty()) {
                    Coding coding = relationship.getCodingFirstRep();
                    relationshipCode = coding.getCode();
                }
            }
        }
    }

    /**
     * Validates whether an approved pre-authorization claim exists for the given related claim ID.
     * Logs a warning if not found — claim proceeds to save and PostSaveValidationService
     * handles the full approved status check (→ SENT_BACK if invalid).
     *
     * @param relatedClaimId
     */
    private void findValidApprovedPreAuth(String relatedClaimId) {
        claimsRepository.findByClaimIdAndUseAndStatus(UUID.fromString(relatedClaimId), Constants.Use.PREAUTHORIZATION, ExtensionStateCodeEnum.APPROVED)
                .ifPresentOrElse(
                        claimDb -> log.info("Approved Pre-Auth found for related claim ID: {}", relatedClaimId),
                        () -> log.warn("Approved Pre-Auth not found for related claim ID: {} at pre-save stage — will be validated post-save", relatedClaimId)
                );
    }


    public PractitionerDetails practitionerUtils(Bundle bundle, Claim claim) {
        PractitionerDetails practitionerDetails = null;
        if (claim.hasEnterer()) {
            String reference;
            reference = claim.getEnterer().getReference();
            Practitioner practitioner = (Practitioner) postOfficeService.getResourceByUrl(bundle, reference, claim.getEnterer().getId());
            if (validatePractitionerWithHie) {
                Practitioner practitionerHIE = validatePractitionerWithHieServer(practitioner.getIdentifier().getFirst().getValue());
                claimsValidator.validatePractitioner(practitioner , practitionerHIE);
                fhirClient.update().resource(practitioner).execute();
            } else {
                fhirClient.update().resource(practitioner).execute();
            }
            if (practitioner != null) {
                claim.setEnterer(new Reference(PRACTITIONER_KEYWORD + practitioner.getIdPart()));
                claim.addContained(practitioner);
            }
            practitionerDetails = practitionerDetailsService.saveToDatabase(Objects.requireNonNull(PractitionerDetails.fromPractitioner(practitioner)));
        }
        return practitionerDetails;
    }

    private void facilityUtils(Bundle bundle, Claim claim) {
        if (!claim.hasFacility()) return;
        String reference = claim.getFacility().getReference();
        Resource resObj = postOfficeService.getResourceByUrl(bundle, reference, claim.getFacility().getId());
        if (resObj != null) {
            String refUrl = (resObj instanceof Organization ? "Organization/" : "Location/") + resObj.getIdPart();
            claim.setProvider(new Reference(refUrl));
            claim.addContained(resObj);
        }
    }

    private void insurerUtils(Bundle bundle, Claim claim) {
        if (!claim.hasInsurer()) return;
        String reference = claim.getInsurer().getReference();
        Organization org = (Organization) postOfficeService.getResourceByUrl(bundle, reference, claim.getInsurer().getId());
        fhirClient.update().resource(org).execute();
        if (org != null) {
            claim.setInsurer(new Reference("Organization/" + org.getId()));
            claim.addContained(org); // Add resources to the transaction bundle
        }
    }

    private Provider providerUtils(Organization organization, String claimID) {

        Provider provider = Provider.fromFhirOrganization(organization);

        if (StringUtils.isEmpty(provider.getLevel())) {
            log.warn("Facility Level not found for Provider: {}", organization.getIdPart());
            throw new RuntimeException("Facility Level not found for Provider: " + organization.getIdPart());
        }

        List<Provider> providers = findProvidersByIdentifier(provider);
        if (providers.isEmpty()) {
            provider = createNewProvider(provider, organization);
        } else {
            provider = updateExistingProvider(providers.getFirst(), provider, organization);
        }
        return provider;
    }

    private Organization getOrganization(Bundle bundle, String reference, Claim claim) {
        Organization organization = (Organization) postOfficeService.getResourceByUrl(bundle, reference, claim.getProvider().getId());
        if (organization == null) {
            throw new RuntimeException("Referenced Provider: " + reference + " not found in bundle.");
        }
        return organization;
    }


    private List<Provider> findProvidersByIdentifier(Provider provider) {
        if (StringUtils.isNotBlank(provider.getMflCode()) && provider.getMflCode().startsWith("MFL-")) {
            return providerRepository.findByMflCode(provider.getMflCode()).orElse(Collections.emptyList());
        } else if (StringUtils.isNotBlank(provider.getFidCode()) && provider.getFidCode().startsWith("FID-")) {
            return providerRepository.findByFidCode(provider.getFidCode()).orElse(Collections.emptyList());
        } else {
            return providerRepository.findByIdentifier(provider.getMflCode()).orElse(Collections.emptyList());
        }
    }

    private Provider createNewProvider(Provider provider, Organization organization) {
        log.warn("Provider not found. Saving to database and FHIR.");
        provider = providerRepository.save(provider);
        updateOrganizationId(organization);
        fhirClient.update().resource(organization).execute();
        log.info("New Provider created. DB ID: {}, FHIR ID: {}", provider.getId(), organization.getIdPart());
        return provider;
    }

    private Provider updateExistingProvider(Provider existingProvider, Provider newProvider, Organization organization) {
        log.info("Updating Provider in FHIR. DB ID: {}, FHIR ID: {}", existingProvider.getId(), organization.getIdPart());

        updateOrganizationId(organization);
        fhirClient.update().resource(organization).execute();

        if (StringUtils.isNotEmpty(newProvider.getLevel()) && !newProvider.getLevel().equalsIgnoreCase(existingProvider.getLevel())) {
            log.info("Updating Facility Level. Old: {}, New: {}", existingProvider.getLevel(), newProvider.getLevel());
            existingProvider.setLevel(newProvider.getLevel());
            existingProvider = providerRepository.save(existingProvider);
        }
        return existingProvider;
    }

    private void updateOrganizationId(Organization organization) {
        String id = organization.getIdPart();
        if (!id.startsWith("MFL-") && !id.startsWith("FID-")) {
            organization.setId("MFL-" + id);
        }
    }

    private void updateClaimWithProviderContained(Claim claim, Organization organization) {
        claim.setProvider(new Reference(organization.getResourceType() + "/" + organization.getIdElement().getIdPart()));
        claim.addContained(organization);
    }

    private void fetchCoverage(Bundle bundle, Claim claim, Patient patient) {
        if (claim.hasInsurance()) {
            for (Claim.InsuranceComponent insuranceComponent : claim.getInsurance()) {
                if (insuranceComponent.hasCoverage()) {
                    String reference = insuranceComponent.getCoverage().getReference();
                    Coverage resource = (Coverage) postOfficeService.getResourceByUrl(bundle, reference, null);
                    resource.setBeneficiary(new Reference(patient.getId()));
                    fhirClient.update().resource(resource).execute();
                    insuranceComponent.setCoverage(new Reference("Coverage/" + resource.getId()));
                    // TODO - Update UI to handle without contained resources.
                    claim.addContained(resource);
                }
            }
        }
    }

    /**
     * Resubmits an insurance claim for processing based on its type (claim or pre-authorization).
     *
     * @param claimId the unique identifier of the insurance claim to be re-submitted
     */
    public void reSubmit(String claimId) {
        try {
            InsuranceClaim iClaim = fetchInsuranceClaim(claimId);

            if (iClaim == null) {
                throw new RuntimeException("Insurance claim not found");
            }
            // Re-route the claim if it was in MISSING_DOCUMENTS state to Resubmitted state
            if(iClaim.getStatus().equals(ExtensionStateCodeEnum.MISSING_DOCUMENTS)){
                surveillanceService.handleResubmittedMissingDocumentsClaim(iClaim, claimId);
            }

            String processType = iClaim.getUse().equalsIgnoreCase("Claim")
                    ? "claimReturned"
                    : "preAuthResubmit";

            if(Constants.RESTRICT_REPROCESS_CLAIM_STATUS.contains(iClaim.getStatus())) {
                throw new BadRequestException(String.format("Claim with claimId : %s has been moved to restricted Status : %s ", claimId , iClaim.getStatus()));
            }

            PublishMessageResponse messageResponse = zeebe
                    .newPublishMessageCommand()
                    .messageName(processType)
                    .correlationKey(claimId)
                    .send()
                    .join();
        } catch (Exception e) {
            throw new RuntimeException("Error while re-submitting claim", e);
        }
    }

    /**
     * Re-processes a claim based on the claim ID provided. This method validates the claim status, retrieves
     * the necessary details, and processes the claim items. If the claim is not in the expected state or any
     * other error occurs during processing, an exception is thrown.
     *
     * @param claimId the unique identifier of the claim to be processed
     */
    public void reProcess(String claimId) {
        try {
            Claim claim = postOfficeService.findClaimById(claimId);
            InsuranceClaim iClaim = fetchInsuranceClaim(claimId);

            if (iClaim == null || !iClaim.getStatus().equals(ExtensionStateCodeEnum.QUEUED)) {
                throw new BusinessException(BUSINESS_EXCEPTION, "Requested Claim is not in Queued state");
            }

            ClaimResponse claimResponse = postOfficeService.getClaimResponseByClaimId(claimId, iClaim.getClaimResponseId());

            claim.getItem().forEach(item -> {
                if (lineItemRepository.findByClaimAndSequence(iClaim, item.getSequence()).isEmpty()) {
                    BenefitsMaster intervention = benefitMasterService.fetchByInterventionCode(item.getProductOrService().getCoding().getFirst().getCode());
                    ClaimLineItem lineItem = ClaimLineItem.fromClaimItem(item, iClaim, intervention.getInterventionName(), null);
                    lineItemRepository.save(lineItem);
                }
            });

            triggerAutoAdjudication(claim, claimResponse);

        } catch (Exception e) {
            throw new RuntimeException("Error while re-processing claim", e);
        }
    }

    public void reProcessReview(String claimId) {
        try {
            Claim claim = postOfficeService.findClaimById(claimId);
            InsuranceClaim iClaim = fetchInsuranceClaim(claimId);

            if (iClaim == null || !iClaim.getStatus().equals(ExtensionStateCodeEnum.CLINICAL_REVIEW) && !iClaim.getStatus().equals(ExtensionStateCodeEnum.IN_REVIEW)) {
                throw new BusinessException(BUSINESS_EXCEPTION, "Requested Claim is not in REVIEW state");
            }

            ClaimResponse claimResponse = postOfficeService.getClaimResponseByClaimId(claimId, iClaim.getClaimResponseId());

            claim.getItem().forEach(item -> {
                if (lineItemRepository.findByClaimAndSequence(iClaim, item.getSequence()).isEmpty()) {
                    BenefitsMaster intervention = benefitMasterService.fetchByInterventionCode(item.getProductOrService().getCoding().getFirst().getCode());
                    ClaimLineItem lineItem = ClaimLineItem.fromClaimItem(item, iClaim, intervention.getInterventionName(), null);
                    lineItemRepository.save(lineItem);
                }
            });
            triggerAutoAdjudication(claim, claimResponse);
        } catch (Exception e) {
            throw new RuntimeException("Error while re-processing claim", e);
        }
    }

    private InsuranceClaim fetchInsuranceClaim(String claimId) {
        Optional<List<InsuranceClaim>> optionalClaim = claimsRepository.existsByClaimId(UUID.fromString(claimId)).orElse(false)
                ? claimsRepository.findByClaimId(UUID.fromString(claimId))
                : claimsRepository.existsByExternalId(claimId).orElse(false)
                ? claimsRepository.findByExternalId(claimId)
                : Optional.empty();

        return optionalClaim.map(List::getFirst).orElse(null);
    }

    /**
     * Asynchronously clears claim responses by processing insurance claims in paginated batches.
     * <p>
     * This method iterates through all insurance claims retrieved from the claims repository
     * and checks for claim responses associated with each claim. For claims with non-empty
     * claim response IDs, it fetches all claim responses using the external claim ID. If
     * multiple claim responses are found for a single claim, it identifies and logs the ones
     * that do not match the expected claim response ID. These identified claim responses are
     * logged for further handling or cleanup.
     * <p>
     * The processing is performed in pages, with a fixed page size, to handle a large number
     * of insurance claims efficiently. After processing each page, the method allows for a
     * delay to avoid overloading dependent systems.
     * <p>
     * The method execution is annotated with {@code @Async} to allow asynchronous processing.
     * This means the method runs in a separate thread and does not block the caller.
     * <p>
     * Key implementation details:
     * - Processes 30 claims per page, iterating through all available pages.
     * - Logs information about multiple and unmatched claim responses for each claim.
     * - Invokes an external service to fetch claim responses using the claim's external ID.
     */
    @Async("virtualThreadExecutor")
    public void clearClaimResponses() {
        final int pageSize = 30;
        int page = 0;
        Page<InsuranceClaim> iClaimsPage;

        do {
            iClaimsPage = claimsRepository.findAll(PageRequest.of(page++, pageSize));

            iClaimsPage.forEach(iClaim -> {
                String actualResponseId = iClaim.getClaimResponseId();

                if (StringUtils.isNotEmpty(actualResponseId)) {
                    Bundle claimResponsesBundle = postOfficeService.getAllClaimResponsesByClaimId(iClaim.getExternalId());

                    if (claimResponsesBundle.hasEntry() && claimResponsesBundle.getEntry().size() > 1) {
                        log.info("Multiple claimResponse ({}) found for claimId {}",
                                claimResponsesBundle.getEntry().size(),
                                iClaim.getClaimId());

                        claimResponsesBundle.getEntry().stream()
                                .map(Bundle.BundleEntryComponent::getResource)
                                .filter(resource -> resource instanceof ClaimResponse)
                                .map(resource -> (ClaimResponse) resource)
                                .filter(claimResponse -> !claimResponse.getIdPart().equalsIgnoreCase(actualResponseId))
                                .forEach(claimResponse -> log.info("Deleting claimResponse {} as it does not match the actual ClaimResponse Id {}",
                                        claimResponse.getIdPart(),
                                        actualResponseId));
                    }
                }
            });

            sleep();

        } while (iClaimsPage.hasNext());
    }

    private void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread was interrupted", e);
        }
    }

    public void updateClaimRequestInfo(String claimId, Map<String, String> updates) {
        Optional<List<InsuranceClaim>> claimOptional = claimsRepository.findByClaimId(UUID.fromString(claimId));

        Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
        if (claim == null) {
            throw new BadRequestException("Claim not Found in fhir Server with Claim ID " + claimId);
        }

        if (claimOptional.isEmpty() || claimOptional.get().isEmpty()) {
            log.info("No matching documents entry found for the provided claim id :{} ", claimId);
            return;
        }

        if (claimOptional.get().size() > 1) {
            log.info("Multiple matching documents found for the provided claim id :{} ", claimId);
            return;
        }

        InsuranceClaim insuranceClaim = claimOptional.get().getFirst();

        if (!insuranceClaim.getUse().equalsIgnoreCase("Claim") && claim.getUse() != CLAIM) {
            throw new BadRequestException(String.format("Requested ClaimId %s if not if Type Claim", claimId));
        }

        if (insuranceClaim.getStatus() != ExtensionStateCodeEnum.CLINICAL_REVIEW && insuranceClaim.getStatus() != ExtensionStateCodeEnum.IN_REVIEW) {
            throw new BadRequestException(String.format("Claim status of claimId %s is not allowed for Update , Status :%s", claimId, claim.getStatus()));
        }

        try {
            Member member = insuranceClaim.getPatient();

            if (updates.containsKey(VAR_BIRTHDATE)) {
                Date date = DATE_FORMAT.parse(updates.get(VAR_BIRTHDATE));
                Date endDate = new Date();
                if (isWithinRange(date, DateUtils.addYears(endDate, -200), endDate)) {
                    member.setBirthDate(date);
                } else {
                    throw new BadRequestException("Birth date is invalid , it is not in Range of Allowed Limit");
                }
            }
            if (updates.containsKey(VAR_GENDER)) {
                String gender = updates.get(VAR_GENDER);
                if (gender.equalsIgnoreCase("male") || gender.equalsIgnoreCase("female")) {
                    member.setGender(gender);
                } else {
                    throw new BadRequestException("Gender value is invalid");
                }
            }
            memberRepository.save(member);
            reProcessReview(claimId);

            ClaimUpdateLogs logs = ClaimUpdateLogs.builder()
                    .claimId(claimId)
                    .username(HealthIdContextHolder.userId())
                    .updateTime(new Date())
                    .oldValue(Map.of("birthDate", insuranceClaim.getPatient().getBirthDate().toString(), "gender", insuranceClaim.getPatient().getGender()))
                    .newValue(updates)
                    .build();
            log.info("Claim Info updated for Claim id :{} with updated Record :{}", claimId, updates);
            claimDataUpdateRepository.save(logs);

        } catch (ParseException e) {
            throw new BadRequestException(String.format("Error while updating claim dat with claimId %s", claimId), e);
        }
    }

    private boolean isWithinRange(Date testDate, Date startDate, Date endDate) {
        return testDate.after(startDate) && (testDate.before(endDate) || DateUtils.isSameDay(testDate, endDate));
    }

    private Provider validateProviderWithHieServer(Organization organization) {
        Provider validatedProvider = null;
        String identifierCode = organization.getIdPart().trim();
        IdentifierType identifierType = IdentifierType.getIdentifier(identifierCode);
        identifierCode = extractFacilityCode(identifierCode);

        // Fetch requestProviderJSON from HIE based on available code with retry mechanism
        String finalIdentifierCode = identifierCode;
        String hieProviderJson = executeWithHieRetry(
                () -> hieHelper.getProviderFromHie(finalIdentifierCode, identifierType),
                "validateProviderWithHie for identifierCode: " + identifierCode
        );
        if (StringUtils.isEmpty(hieProviderJson) || hieProviderJson.equalsIgnoreCase("null")) {
            log.warn("HIE server returned an empty response for Provider Code: {}", hieProviderJson);
            return null;
        }
        Organization organizationHIE = validateProviderHie(organization, hieProviderJson);

        organizationHIE.setId(organizationHIE.getIdElement().getIdPart());
        //save/update to DB
        Provider parsedProvider = Provider.fromJsonBundle(hieProviderJson);
        if (parsedProvider != null) {
            log.info("Processing Provider FidCode: {}", parsedProvider.getFidCode());
            Optional<Provider> response = providerRepository.findOneByFidCode(parsedProvider.getFidCode());
            validatedProvider = providerService.saveToDatabase(parsedProvider, response);
        }
        // save or update in FHIR
        log.info("Provider : {}  in FHIR. Creating/updating a  entry...", identifierCode);
        MethodOutcome outcome = fhirClient.update().resource(organizationHIE).execute();
        log.info("Created/updated Provider resource ID: {}", outcome.getId());

        // Getting the bedOccupancy -> SUPPORT-70
        if (facilityBedOccupancy) {
            List<BedOccupancyDto> bedOccupancy = facilityBedOccupancyService.extractBedOccupancy(hieProviderJson);
            if (!bedOccupancy.isEmpty()) {
                int sizeOfFacility = facilityBedOccupancyService.saveBedOccupancy(bedOccupancy);
                log.debug("Size of facility: {}", sizeOfFacility);
            }
        }

        return validatedProvider;
    }

    public String validateProviderWithHieServer(String providerId ,  IdentifierType identifierType , String providerLevel) {
        try {
            // Fetch requestProviderJSON from HIE based on available code with retry mechanism
            String hieProviderJson = executeWithHieRetry(
                    () -> hieHelper.getProviderFromHie(providerId, identifierType),
                    "validateProviderWithHie for providerId: " + providerId
            );
            if (StringUtils.isBlank(hieProviderJson) || hieProviderJson.equalsIgnoreCase("null")) {
                return String.format("HIE server returned an empty response for Provider Code: %s", providerId);
            }
            return validateProviderHie(providerId, providerLevel, hieProviderJson);
        } catch (Exception e) {

            return String.format("Unexpected error while fetching provider data from HIE. ProviderId: %s. Message: %s",
                    providerId, e.getMessage());
        }
    }

    private Organization validateProviderHie(Organization organization, String hieProviderJson) {
        Organization organizationHIE = fhirContext.newJsonParser().parseResource(Organization.class, hieProviderJson);
        // Validate the HIE Organization with Provider Organization
        providerHelper.validateAndMismatch(organization, organizationHIE);
        return organizationHIE;
    }

    private String validateProviderHie(String providerId, String providerLevel , String hieProviderJson) {
        Organization organizationHIE = fhirContext.newJsonParser().parseResource(Organization.class, hieProviderJson);
        // Validate the HIE Organization with Provider Organization
        return providerHelper.validateFacilityLevel(providerHelper.getFacilityLevel(organizationHIE, "keph-level"), providerLevel , providerId);
    }

    public static String extractFacilityCode(String identifierCode) {
        if (identifierCode != null && identifierCode.startsWith("MFL-")) {
            return identifierCode.replace("MFL-", "");
        }
        return identifierCode;
    }

    /**
     * Executes the given supplier with retry logic for HIE timeout scenarios.
     * Retries on timeout-related exceptions up to the configured max attempts.
     *
     * @param supplier    The operation to execute
     * @param operationName Name of the operation for logging purposes
     * @param <T>         Return type of the operation
     * @return The result of the supplier
     * @throws RuntimeException if all retry attempts fail
     */
    private <T> T executeWithHieRetry(Supplier<T> supplier, String operationName) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < hieRetryMaxAttempts) {
            attempt++;
            try {
                return supplier.get();
            } catch (Exception e) {
                lastException = e;
                log.info("HIE {} attempt {}/{} failed: {}",
                        operationName, attempt, hieRetryMaxAttempts, e.getMessage());
                if (attempt < hieRetryMaxAttempts) {
                    try {
                        Thread.sleep(hieRetryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted for " + operationName, ie);
                    }
                }
            }
        }

        log.error("HIE {} failed after {} attempts", operationName, hieRetryMaxAttempts);
        throw new RuntimeException("HIE " + operationName + " failed after " + hieRetryMaxAttempts + " attempts", lastException);
    }

    public Object updatedRelatedInfo(String claimId, ArrayNode relatedInfo) {

        if (relatedInfo.size() > 1) {
            throw new BadRequestException("Invalid request only add related Component allowed against this Claim:" + claimId);
        }

        // Validate the request
        FhirDocumentUtils.validateRelatedInfo(relatedInfo);
        try {
            // Fetch the existing Claim resource
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();

            if (!claim.getRelated().isEmpty()) {
                throw new BadRequestException(String.format("Claim id %s already contain related Component", claimId));
            }

            // Process and add new attachments
            JsonNode node = relatedInfo.get(0);
            Claim.RelatedClaimComponent relatedClaimComponent = FhirDocumentUtils.createRelatedComponent(node);

            //Verify Claim
            Optional<List<InsuranceClaim>> claims = claimsRepository.findByClaimId(UUID.fromString(claimId));

            if (claims.isEmpty()) {
                throw new BadRequestException(String.format("Claim id %s does not exist", claimId));
            }

            if (claims.get().size() > 1) {
                throw new BadRequestException("Multiple claims found against this ClaimID:" + claimId);
            }

            if (claims.get().getFirst().getStatus() != ExtensionStateCodeEnum.CLINICAL_REVIEW) {
                throw new BadRequestException(String.format("Claim id %s is not in Clinical Review state", claimId));
            }

            //Verify PreAuth

            String preAuthId = relatedClaimComponent.getClaim().getIdentifier().getValue();

            Optional<List<InsuranceClaim>> preAuths = claimsRepository.findByClaimId(UUID.fromString(preAuthId));

            if (preAuths.isEmpty()) {
                throw new BadRequestException(String.format("Pre Auth id %s does not exist", preAuthId));
            }

            if (preAuths.get().size() > 1) {
                throw new BadRequestException("Multiple Pre Auths found against this ClaimID:" + preAuthId);
            }

            if (preAuths.get().getFirst().getStatus() != ExtensionStateCodeEnum.APPROVED) {
                throw new BadRequestException(String.format("Pre Auth id %s is not in APPROVED state", claimId));
            }

            if (claims.get().getFirst().getPatient().getId() != preAuths.get().getFirst().getPatient().getId()) {
                throw new BadRequestException(String.format("Patient Id of Claim  %s and Patient Id of Pre Auth %s do not match", claims.get().getFirst().getPatient().getId(), preAuths.get().getFirst().getPatient().getId()));
            }

            Optional<List<InsuranceClaim>> preAuthExist = claimsRepository.findByPreAuthId(UUID.fromString(preAuthId));

            if (preAuthExist.isPresent() && !preAuthExist.get().isEmpty()) {
                throw new BadRequestException("PreAuth exists against this ClaimID:" + preAuthExist.get().stream().map(cl -> (cl.getClaimId().toString())).collect(Collectors.joining(",", "[", "] ")));
            }

            BigDecimal claimAmount = claims.get().getFirst().getClaimedTotal() != null ? claims.get().getFirst().getClaimedTotal() : BigDecimal.ZERO;
            BigDecimal preAuthAmount = preAuths.get().getFirst().getClaimedTotal() != null ? preAuths.get().getFirst().getClaimedTotal() : BigDecimal.ZERO;

            if (claimAmount.compareTo(preAuthAmount) > 0) {
                throw new BadRequestException("Approved Amount in PreAuth %s Request is greater than Approved Amount in Claim %s" + claimId);
            }

            claim.addRelated(relatedClaimComponent);

            // Update the Claim resource on the FHIR server
            fhirClient.update().resource(claim).execute();

            InsuranceClaim claimToSave = claims.get().getFirst();

            claimToSave.setPreAuthId(UUID.fromString(preAuthId));
            claimsRepository.updatePreAuthIdByClaimId(UUID.fromString(preAuthId), claimToSave.getClaimId());

            reProcessReview(claimId);

            ClaimUpdateLogs logs = ClaimUpdateLogs.builder()
                    .claimId(claimId)
                    .username(HealthIdContextHolder.userId())
                    .updateTime(new Date())
                    .oldValue(Map.of())
                    .newValue(Map.of("preAuthId", preAuthId))
                    .build();

            log.info("Claim Info updated for Claim id :{} with updated Record :{}", claimId, preAuthId);

            claimDataUpdateRepository.save(logs);

            return Map.of(
                    "Message", "Related Info added successfully",
                    "claimId", claimId,
                    "preAuthId", preAuthId);
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException(e.getMessage());
        } catch (DataValidationException e) {
            log.warn("Error in validating Related Info {}", claimId, e);
            throw new DataValidationException("$$" + e.getMessage());
        }
    }

    // for update bundle  Flow
    public String updateBundle(String bundleJson) {
        Bundle inputBundle = parseAndValidateBundle(bundleJson);
        Claim claim = extractClaim(inputBundle);

        // 1. Validating Patient Objects from HIE.
        Patient providerPatient = extractPatient(inputBundle, claim);
        String patientId = providerPatient.getIdPart();

        if (validateMemberWithHie) {
            log.info("Validating Beneficiary from HIE, ID :  {}", patientId);
            String patientJsonHie = hieHelper.getPatientFromHieViaShaNumber(patientId);
            validatePatientHie(providerPatient, patientId, patientJsonHie);
        } else {
            log.info("Skipping HIE member validation for resubmit (validateMemberWithHie=false)");
        }

        // Validate if the claim has a valid SHA code and also the 12 days rule for SHA-03 interventions.
        validateSHACode(claim);

        // Handle existing claim for duplicate requests.
        String externalClaimId = claim.getIdPart();
        Optional<List<InsuranceClaim>> claimIDResponse =
                claimsRepository.findByClaimId(UUID.fromString(externalClaimId));

        InsuranceClaim insuranceClaim = claimIDResponse
                .flatMap(list -> list.stream().findFirst()) // Retrieve first element safely
                .orElseThrow(() -> new DataNotFoundException("Claim not found for ID: " + externalClaimId));

        insuranceClaim = component.resubmitClaimBundle(externalClaimId, claim, insuranceClaim);
        claimsRepository.save(insuranceClaim);
        String response = fhirParser.encodeResourceToString(inputBundle);
        ClaimBundleLogEntity logEntity =
                component.transformInEntity(response, insuranceClaim.getClaimId().toString());
        Long id = logService.saveClaimBundle(logEntity);
        log.info("Claim bundle log entry created with ID: {}", id);
        boolean isResubmittedMissingDocument = insuranceClaim.getStatus().equals(ExtensionStateCodeEnum.RESUBMITTED_MISSING_DOCUMENTS);
        if(!isResubmittedMissingDocument){
            // Submit claim to Reprocessing workflow if not in Missing Documents state
            log.info("Submitting claim to reprocessing workflow, Claim ID: {}", externalClaimId);
            //Fix workflow correlation issue as this resumes the existing workflow at the intermediate catch event
            reSubmit(externalClaimId);
        }
        return fhirParser.encodeResourceToString(inputBundle);
    }

    /**
     * Validate if the claim has a valid SHA code and also the 12 days rule for SHA-03 interventions.
     * @param claim
     */
    private void validateSHACode(Claim claim) {
        for (var item : claim.getItem()) {
            Optional.ofNullable(item.getProductOrService())
                    .map(CodeableConcept::getCoding)
                    .filter(codingList -> !codingList.isEmpty())
                    .map(List::getFirst)
                    .map(Coding::getCode)
                    .filter(Objects::nonNull)
                    .ifPresent(code -> {
                        String[] parts = code.split("-");

                        // Ensure the code format is correct: PREFIX-NUM-NUM
                        if (parts.length != 3) {
                            throw new BusinessException(INVALID_INTERVENTION_CODES, "Invalid SHA code: " + code);
                        }

                        // Validate second and third parts are numeric
                        if (!parts[1].matches("\\d+") || !parts[2].matches("\\d+")) {
                            throw new BusinessException(INVALID_INTERVENTION_CODES, "Invalid SHA code: " + code);
                        }

                        // [MIGRATED TO PostSaveValidationService] Maternity 10-month lookback
                        // [MIGRATED TO PostSaveValidationService] SHA code combinations
                        // [MIGRATED TO PostSaveValidationService] SHA code duration

                    });
        }
    }

    /**
     * Validates only the SHA code format (PREFIX-NUM-NUM).
     * Business validations (maternity, combinations, duration) are handled by PostSaveValidationService.
     */
    private void validateSHACodeFormat(Claim claim) {
        for (var item : claim.getItem()) {
            Optional.ofNullable(item.getProductOrService())
                    .map(CodeableConcept::getCoding)
                    .filter(codingList -> !codingList.isEmpty())
                    .map(List::getFirst)
                    .map(Coding::getCode)
                    .filter(Objects::nonNull)
                    .ifPresent(code -> {
                        String[] parts = code.split("-");
                        if (parts.length != 3) {
                            throw new BusinessException(INVALID_INTERVENTION_CODES, "Invalid SHA code: " + code);
                        }
                        if (!parts[1].matches("\\d+") || !parts[2].matches("\\d+")) {
                            throw new BusinessException(INVALID_INTERVENTION_CODES, "Invalid SHA code: " + code);
                        }
                    });
        }
    }

    public void claimReProcessReview(String claimId, String bundleId) {
        try {
            Claim claim = postOfficeService.findClaimById(claimId);
            InsuranceClaim iClaim = fetchInsuranceClaim(claimId);
            ClaimResponse claimResponse = postOfficeService.getClaimResponseByClaimId(claimId, iClaim.getClaimResponseId());
            claim.getItem().forEach(
                            item -> {
                                if (lineItemRepository.findByClaimAndSequence(iClaim, item.getSequence()).isEmpty()) {
                                    BenefitsMaster intervention =benefitMasterService.fetchByInterventionCode(
                                                    item.getProductOrService().getCoding().getFirst().getCode());
                                    ClaimLineItem lineItem =ClaimLineItem.fromClaimItem(item, iClaim, intervention.getInterventionName(), null);
                                    lineItemRepository.save(lineItem);
                                }
                            });
            triggerAutoAdjudication(claim, claimResponse);
        } catch (Exception e) {
            throw new RuntimeException("Error while re-processing claim", e);
        }
    }

    private void submitToAINotesEngine(UUID claimId, Bundle inputBundle) {

        log.info("Submitting Claim and ClaimResponse to AI Notes Engine: ClaimID: {}, ResponseID: {}",
                claimId, inputBundle);
    }

    private void validatePatientAge(Patient patient, Claim claim) {
        List<Claim.ItemComponent> items = claim.getItem();
        LocalDate latestServicedEndDate = null;
        if(!claim.hasItem()){
            throw new BusinessException(VALIDATE_DATA_CANNOT_BLANK, "Missing items in the FHIR bundle.");
        }
        for (Claim.ItemComponent item : items) {
            List<Coding> codings = item.getProductOrService().getCoding();
            for (Coding coding : codings) {
                if (SHA_03_AGE_VALIDATION.equalsIgnoreCase(coding.getCode())) {
                    Period servicedPeriod = item.getServicedPeriod();
                    if (servicedPeriod != null && servicedPeriod.hasEnd()) {
                        LocalDate endDate = servicedPeriod.getEnd().toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate();
                        if (latestServicedEndDate == null || endDate.isAfter(latestServicedEndDate)) {
                            latestServicedEndDate = endDate;
                        }
                    }
                }
            }
        }
        if (latestServicedEndDate == null) {
            // No SHA-03-003 found — skip
            return;
        }
        if (!patient.hasBirthDate()) {
            throw new BusinessException(BAD_REQUEST,
                    "Patient birth date is missing. Cannot validate age for intervention SHA-03-003.");
        }
        LocalDate birthDate = patient.getBirthDate().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        long ageInDays = ChronoUnit.DAYS.between(birthDate, latestServicedEndDate);
        if (ageInDays > 28) {
            throw new BusinessException(BAD_REQUEST,
                    "Patient age exceeds 28 days for intervention SHA-03-003. This claim cannot be submitted.");
        }
    }
}
