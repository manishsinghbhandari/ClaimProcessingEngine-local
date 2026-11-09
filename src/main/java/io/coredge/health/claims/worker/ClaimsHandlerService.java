package io.coredge.health.claims.worker;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.google.gson.Gson;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.*;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.db.repositories.ProviderRepository;
import io.coredge.health.claims.enums.BenefitMasterSearchType;
import io.coredge.health.claims.models.PatientLimit;
import io.coredge.health.claims.service.*;
import io.coredge.health.claims.service.intervention.InterventionInpatientLOSRulesService;
import io.coredge.health.claims.service.intervention.InterventionValidationService;
import io.coredge.health.claims.utils.BifurcationService;
import io.coredge.health.claims.utils.ClaimsHandlerUtility;
import io.coredge.health.claims.utils.FhirHelper;
import io.coredge.health.claims.utils.PreAuthLimits;
import io.coredge.health.claims.config.properties.ApplicationProperties;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.Adjudication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.APPROVED_EXTENSION_STATES;
import static io.coredge.health.claims.constants.Constants.SubType.*;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_CLAIM_AMT;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_HAS_PRE_AUTH;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_HOUSE_HOLD_USE_CNT;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_INTERVENTIONS;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_INTERVENTION_USE_CNT;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_IS_CIVIL_SERVANT_CLAIM;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_IS_DOC_APPROVAL_REQ;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_IS_PHC_CLAIM;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_ITEM_SEQUENCE;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_LOS;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_SERVICE_CATEGORY;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_SHA_CODE;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_STATUS;

@Slf4j
@Service
public class ClaimsHandlerService {

    private final ApplicationProperties.AdjudicationRules adjudicationRulesProperties;
    private final ApplicationProperties.Erp erpProperties;
    private final ApplicationProperties.FeatureFlags featureFlagsProperties;
    private final ApplicationProperties.Ai aiProperties;

    @Autowired
    private WorkflowService wfService;

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private BenefitsMasterRepository benefitsRepository;

    @Autowired
    private ClaimExtensionService extentionService;

    @Autowired
    private InsuranceClaimsRepository claimsRepository;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private LiviaClaimService liviaClaimService;

    @Autowired
    private MaternityApprovalService maternityApprovalService;

    @Autowired
    private PreAuthLimits preAuthLimits;

    @Autowired
    private BifurcationService bifurcationService;

    @Autowired
    private InterventionValidationService interventionValidationService;

    @Autowired
    private InterventionInpatientLOSRulesService inpatientLOSRulesService;

    @Autowired
    private ClaimsHandlerUtility claimsHandlerUtility;

    @Autowired
    private TariffThresholdService tariffThresholdService;

    public static final String SHA_CODE = "shaCode-";
    public static final String PMF = "PMF";

    @Autowired
    private ProcessNotesService processNotesService;

    public ClaimsHandlerService(ApplicationProperties.AdjudicationRules adjudicationRulesProperties,
                                ApplicationProperties.Erp erpProperties,
                                ApplicationProperties.FeatureFlags featureFlagsProperties,
                                ApplicationProperties.Ai aiProperties) {
        this.adjudicationRulesProperties = adjudicationRulesProperties;
        this.erpProperties = erpProperties;
        this.featureFlagsProperties = featureFlagsProperties;
        this.aiProperties = aiProperties;
    }

    /**
     * Populate params for auto-adjudication process.
     *
     * @param claimId         claimID
     * @param workflowParams  workflowParams
     */
    public void populateAdjudicationData(String claimId, Map<String, Object> workflowParams) {
        Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
        //ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId).execute();
        InsuranceClaim iClaim = claimsRepository.findByExternalId(claimId).get().getFirst();
        Member patient = iClaim.getPatient();
        boolean isPhcClaim = iClaim.isPHCClaim();
        boolean isDocApprovalRequired = false;

        String gender = patient.getGender();
        int age = FhirHelper.calculateAge(patient.getBirthDate());

        // Checking if claim contains PMF coverage then marking this as a Civil Servant Claim.
        boolean isCivilServantClaim = false;
        boolean hasPmfCoverage = claim.hasInsurance() && claim.getInsurance().stream()
                .anyMatch(insurance -> insurance.getCoverage().getReference().toLowerCase().contains("pmf"));
        boolean isLiviaClaim = Optional.ofNullable(iClaim.getSource())
                .map(String::toUpperCase)
                .map(val -> val.equals(Constants.RequestSource.LIVIA.toString()))
                .orElse(false);
        if (hasPmfCoverage) {
            isCivilServantClaim = true;
            log.info("Claim {} is Civil Servant Claim.", claimId);
        }

        String providerLevel = "";
        if (iClaim.getProvider() != null) {
            Provider provider = iClaim.getProvider();
            providerLevel = provider.getLevel();
            if (!StringUtils.hasText(providerLevel)) {
                throw new RuntimeException("Facility level not found.");
            }
        }

        String serviceCatagory = ""; // Defaults to inpatient as it can be any.
        if (claim.hasSubType()) {
            serviceCatagory = claim.getSubType().getText();
            String sybType = claim.getSubType().getCoding().getFirst().getCode();
            serviceCatagory = switch (sybType) {
                case OUTPATIENT -> VAR_OUTPATIENT;
                case INPATIENT -> VAR_INPATIENT;
                case EMERGENCY -> VAR_EMERGECNY;
                default -> serviceCatagory;
            };
        }

        // Prepare Claim level Inputs for workflow. Item level inputs will be added.
        List<Map<String, Object>> wfInterventionInputs = new ArrayList<>();
        // Add adjudication and other details (this is simplified)
        int count = 1;
        for (Claim.ItemComponent item : claim.getItem()) {
            boolean hasPreAuth;
            int lengthOfStay = FhirHelper.calculateLoS(claim);
            // If per-diem items so they will have different serviced period and so different LOS.
            if (item.hasServicedPeriod() && (item.getServicedPeriod().hasStart() && item.getServicedPeriod().hasEnd())) {
                lengthOfStay = FhirHelper.calculateLoS(item.getServicedPeriod().getStart(), item.getServicedPeriod().getEnd());
            }
            Map<String, Object> adjudicationDecisionInputs = new HashMap<String, Object>();
            Coding intervention = item.getProductOrService().getCoding().getFirst();
            String interventionCode = intervention.getCode();
            workflowParams.put("shaCode-" + count, interventionCode);
            count++;
            adjudicationDecisionInputs.put(VAR_GENDER, gender);
            adjudicationDecisionInputs.put(VAR_AGE, age);

            adjudicationDecisionInputs.put(VAR_INDIVIDUAL_USAGE_CNT, 1);
            adjudicationDecisionInputs.put(VAR_SERVICE_CATEGORY, serviceCatagory);
            adjudicationDecisionInputs.put(VAR_SHA_CODE, interventionCode);
            adjudicationDecisionInputs.put(VAR_ITEM_SEQUENCE, item.getSequence());
            adjudicationDecisionInputs.put(VAR_CLAIM_AMT, item.getNet().getValue().doubleValue());
            adjudicationDecisionInputs.put(VAR_LOS, lengthOfStay);

            //TODO: Get this data fixed. This is to be handled during adjudication.
            adjudicationDecisionInputs.put(VAR_INTERVENTION_USE_CNT, 1);
            adjudicationDecisionInputs.put(VAR_HOUSE_HOLD_USE_CNT, 1);

            ClaimLineItem claimItem = lineItemRepository.findByClaimAndSequence(iClaim, item.getSequence()).get().getFirst();
            if (isCivilServantClaim) {
                boolean isPMSFCoverage = benefitsRepository.existsByInterventionCodeAndExactAppliesToSchemes(interventionCode,
                        BenefitMasterSearchType.CoverageType.PMSF.getValue());
                if(!isLiviaClaim){
                    claimItem.setPmfItem(isPMSFCoverage);
                }else{
                    claimItem.setStatus(Constants.ClaimLineItemStatus.PASSED);
                    claimItem.setApprovedPrice(claimItem.getNetValue());
                    claimItem.setJustification("Auto Approved.");
                }
//                if (interventionCode.contains("PMF")) {
//                    claimItem.setPmfItem(true);
//                }
            }

            if (claim.getUse().equals(Claim.Use.CLAIM)) {
                hasPreAuth = claimItem.getPreAuthId() != null;
                //Only send it for Claims and not for preAuths.
                adjudicationDecisionInputs.put(VAR_HAS_PRE_AUTH, hasPreAuth);
            }
            claimItem.setAdjudicationParams(new Gson().toJson(adjudicationDecisionInputs));
            wfInterventionInputs.add(adjudicationDecisionInputs);
            lineItemRepository.save(claimItem);

            if (benefitsRepository.existsByInterventionCode(interventionCode)) {
                BenefitsMaster benefit = benefitsRepository.findByInterventionCode(interventionCode).get().getFirst();
                if (claim.getUse().equals(Claim.Use.CLAIM)) {
                    if (adjudicationRulesProperties.getServicesNeedsDocClaimApproval().stream().anyMatch(benefit.getShaCategory()::contains) ||
                            adjudicationRulesProperties.getServicesNeedsDocClaimApproval().stream().anyMatch(benefit.getCode()::contains) ||
                            adjudicationRulesProperties.getServicesNeedsDocClaimApproval().stream().anyMatch(benefit.getInterventionCode()::contains)) {
                        isDocApprovalRequired = true;
                    }

                    //  // For code -SHA-08 , Maternity and New Born Maternity
                    if (!isDocApprovalRequired) {
                        isDocApprovalRequired = maternityApprovalService.isDocApprovalMaternityNewBornHealth(interventionCode, patient);
                    }

                    if (lengthOfStay > adjudicationRulesProperties.getLosReqDocAuth() && providerLevel.contains(adjudicationRulesProperties.getLevelReqDocAuth()) && interventionCode.contains("SHA-13")) {
                        isDocApprovalRequired = true;
                    }
                    if (lengthOfStay > adjudicationRulesProperties.getLosReqDocAuthIp() && providerLevel.contains(adjudicationRulesProperties.getLevelReqDocAuth()) && interventionCode.contains("SHA-07")) {
                        isDocApprovalRequired = true;
                    }

                    // Check inpatient LOS rules from InterventionInpatientLosRule table to determine if medical review is required.
                    if (!isDocApprovalRequired && claim.getUse().equals(Claim.Use.CLAIM)) {
                        String packageCode = interventionCode.substring(0, interventionCode.lastIndexOf("-"));
                        log.info("packageCode {}", packageCode);
                        boolean requiresReviewByLosRule = inpatientLOSRulesService.checkInpatientLosRule(serviceCatagory, lengthOfStay, providerLevel, interventionCode, packageCode, iClaim.getProvider().getFacilityAgent());
                        log.info("requiresReviewByLosRule {}", requiresReviewByLosRule);
                        if (requiresReviewByLosRule) {
                            isDocApprovalRequired = true;
                            log.info("Inpatient LOS rule triggered medical review for ClaimId: {}, " +
                                    "InterventionCode: {}, LOS: {}, ProviderLevel: {}", claimId, interventionCode, lengthOfStay, providerLevel);
                        }
                    }
                    log.info("Setting [CLAIMS] [{}] medical review required as {} for claimId :{}", benefit.getShaCategory(), isDocApprovalRequired, claim.getId());
                } else {
                    if (adjudicationRulesProperties.getServicesNeedsDocPreAuthApproval().stream().anyMatch(benefit.getShaCategory()::contains) ||
                            adjudicationRulesProperties.getServicesNeedsDocPreAuthApproval().stream().anyMatch(benefit.getCode()::contains) ||
                            adjudicationRulesProperties.getServicesNeedsDocPreAuthApproval().stream().anyMatch(benefit.getInterventionCode()::contains)) {
                        isDocApprovalRequired = true;
                    }
                    log.info("Setting [PRE-AUTH] [{}]  Doctors Approval required as {} for claimId :{}", benefit.getShaCategory(), isDocApprovalRequired, claim.getId());
                }
            } else {
                log.warn("Intervention Code {} used in ClaimId {} do not exists in DB.", interventionCode, claimId);
            }
        }
        // Skip manual review for LIVIA claims only.
        boolean manualReviewReq = featureFlagsProperties.isClaimsManualReview();
        boolean isEfwaApproved = Boolean.TRUE.equals(workflowParams.get(VAR_IS_EFWA_APPROVED));
        boolean isEwaRequireDoctorReview = Boolean.TRUE.equals(workflowParams.get(VAR_IS_EFWA_REQUIRE_DOCTOR_REVIEW));
        boolean isAiApprovedClaim = Boolean.TRUE.equals(workflowParams.get("isAiApprovedClaim"));
        if (isLiviaClaim) {
            manualReviewReq = false;
            isDocApprovalRequired = false;
            log.info("Skipping manual review and doc approval for LIVIA claimId: {}", claimId);
        } else if (!isAiApprovedClaim && isEfwaApproved && !isEwaRequireDoctorReview) {
            manualReviewReq = false;
            if (claim.getUse().equals(Claim.Use.CLAIM)) {
                isDocApprovalRequired = false;
            }
        }
        workflowParams.put(VAR_MANUAL_REVIEW_REQ, manualReviewReq);
        workflowParams.put(VAR_IS_DOC_APPROVAL_REQ, isDocApprovalRequired);
        workflowParams.put(VAR_START_DATE, FhirHelper.convertToLocalDateFromDateTime(claim.getBillablePeriod().getStart()));
        workflowParams.put(VAR_END_DATE, FhirHelper.convertToLocalDateFromDateTime(claim.getBillablePeriod().getEnd()));
        workflowParams.put(VAR_IS_PHC_CLAIM, isPhcClaim);
        workflowParams.put(VAR_STATUS, VAR_STATUS_PROCESSING);
        workflowParams.put(VAR_INTERVENTIONS, wfInterventionInputs);
        workflowParams.put(VAR_IS_CIVIL_SERVANT_CLAIM, isCivilServantClaim);
    }

    public Map<String, Object> autoAdjudicate(ActivatedJob job) {
        // 1. Initial Setup and Data Retrieval
        Map<String, Object> workflowParams = job.getVariablesAsMap();
        String claimId = (String) job.getVariable("claimId");
        log.info("Starting Auto-Adjudication Process for ClaimID: {}", claimId);
        Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
        InsuranceClaim iClaim = claimsRepository.findByExternalId(claimId)
                .orElseThrow(() -> new RuntimeException("Invalid Request, No Claim found for ClaimID: " + claimId))
                .getFirst();
        log.info("START → ClaimResponse fetch | Raw ID: {} | Claim No: {}", iClaim.getClaimResponseId(), iClaim.getClaimId());
        // Extract clean ID
        String cleanId = extractId(iClaim.getClaimResponseId());
        ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(cleanId).execute();
        log.info("END → ClaimResponse fetched successfully | Raw ID: {} | Clean ID: {} | Claim No: {}", iClaim.getClaimResponseId(), cleanId, iClaim.getClaimId());
        // 2. Provider Level Validation
        String providerLevel = Optional.ofNullable(iClaim.getProvider())
                .map(Provider::getLevel)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new RuntimeException("Facility level not found."));

        boolean isCivilServantClaim = Boolean.TRUE.equals(job.getVariable(VAR_IS_CIVIL_SERVANT_CLAIM));

        // 3. Clear existing adjudications
        claimResponse.getItem().clear();
        claimResponse.getTotal().clear();

        // 4. Prepare processing variables
        List<Map<String, Object>> interventions = (List<Map<String, Object>>) workflowParams.get("interventions");
        Map<String, Boolean> resultMap = new HashMap<>();
        Map<String, BigDecimal> pmfAmountMap = new HashMap<>();
        boolean isLiviaClaim = Optional.ofNullable(iClaim.getSource())
                .map(String::toUpperCase)
                .map(val -> val.equals(Constants.RequestSource.LIVIA.toString()))
                .orElse(false);

        boolean isAutoApproved = isLiviaClaim;

        // Track SHA-19 reduction state per service date (first-item tracker)
        Map<LocalDate, Boolean> sha19FirstProcessed = new HashMap<>();
        String fullAmountTakenCode = null;
        for (Map<String, Object> adjudicationInputs : interventions) {
            int sequence = (int) adjudicationInputs.get(VAR_SEQUENCE);
            ClaimLineItem claimItem = lineItemRepository.findByClaimAndSequence(iClaim, sequence).orElseThrow().getFirst();

            // Skip civil servant items
            if (claimItem.isPmfItem() || isLiviaClaim) {
                continue;
            }

            String paramsJson = claimItem.getAdjudicationParams();
            Map<String, Object> decisionInputs = Map.of();
            if (StringUtils.hasText(paramsJson)) {
                try {
                    decisionInputs = new Gson().fromJson(paramsJson, Map.class);
                } catch (Exception ex) {
                    log.warn("!!Error while converting to lineItemInputs for line_item_id: {} and claimId {}", claimItem.getId(), iClaim.getClaimId());
                }
            }
            //Note: Use data from WF in case its not in DB to support old workflows.
            if (decisionInputs.isEmpty()) {
                decisionInputs = adjudicationInputs;
            }

            ClaimResponse.ItemComponent responseItem = new ClaimResponse.ItemComponent();
            responseItem.setItemSequence(sequence);
            String interventionCode = (String) decisionInputs.get(VAR_SHA_CODE);
            String packageCode = interventionCode.substring(0, interventionCode.lastIndexOf("-"));

            List<BenefitsMaster> benefits = benefitsRepository.findByInterventionCode(interventionCode).orElse(Collections.emptyList());
            if (benefits.isEmpty()) {
                log.warn("No BenefitsMaster found for interventionCode: {} on claim: {} — skipping line item adjudication", interventionCode, claimId);
                continue;
            }
            BenefitsMaster benefit = benefits.getFirst();

            //Note: Re-read level from DB to ensure if there is a change in DB & we need to re-run workflow.
            if (StringUtils.hasText(providerLevel)) {
                log.info("Using Provider level from DB for Claim:{} instead of workflow. New level: {}", claimId, providerLevel);
                decisionInputs.put(VAR_FACILITY_CATEGORY, providerLevel.toLowerCase());
            } else {
                throw new RuntimeException("Facility level not found.");
            }

            String patientCrId = iClaim.getPatient().getCrId();
            LocalDate policyStartDate = iClaim.getPolicyStartDate() != null ? iClaim.getPolicyStartDate().toLocalDate() : null;
            LocalDate policyEndDate = iClaim.getPolicyEndDate() != null ? iClaim.getPolicyEndDate().toLocalDate() : null;
            UUID currentClaimId = iClaim.getClaimId();

            String use;
            if (claim.getUse().equals(Claim.Use.PREAUTHORIZATION)) {
                use = Constants.Use.PREAUTHORIZATION;
            } else {
                use = Constants.Use.CLAIM;
            }

            PatientLimit patientLimit ;

            // PMF First Charge Implementation.
            if (isCivilServantClaim && benefit.getCombinedCodes() != null) {
                String newInterventionCode = benefit.getCombinedCodes();
                patientLimit = fetchLimitsOverride(iClaim.getPatient().getHouseholdNumber(), iClaim.getPatient().getCrId(), use,
                        newInterventionCode, policyStartDate, policyEndDate, currentClaimId);
            } else {
                patientLimit = fetchLimits(iClaim.getPatient().getHouseholdNumber(), iClaim.getPatient().getCrId(), use,
                        packageCode, interventionCode, policyStartDate, policyEndDate, currentClaimId, true);
            }

            Claim.ItemComponent filteredItem = claim.getItem().stream().filter(item -> item.getSequence() == sequence)
                    .findFirst().orElse(null);

            BigDecimal requestedAdjAmount = BigDecimal.ZERO;
            if (filteredItem != null) {
                requestedAdjAmount = filteredItem.getNet().getValue();
            }

            // ===========================================================
            // 🔥 SHA-19 PRICE REDUCTION LOGIC (YOUR REQUEST)
            // ===========================================================

            BigDecimal finalApprovedPrice = requestedAdjAmount; // default value
            if (matchesBlock(claimItem.getCode(), "SHA-19")) {
                LocalDate serviceDate = claimItem.getServicedPeriodStart();
                BigDecimal original = requestedAdjAmount;
                if (original == null) original = BigDecimal.ZERO;

                if (!sha19FirstProcessed.containsKey(serviceDate)) {
                    // The First SHA-19 item of that service date → full price
                    sha19FirstProcessed.put(serviceDate, true);
                    finalApprovedPrice = original;
                    fullAmountTakenCode = claimItem.getCode();
                } else {
                    // All other SHA-19 items of same date → 50% of their own price
                    finalApprovedPrice = original.multiply(new BigDecimal("0.5"));
                }
            }

            // Tariff threshold logic (e.g., SHA-03 12-day rule)
            Object losObj = decisionInputs.get(VAR_LOS);
            int los = (losObj instanceof Number) ? ((Number) losObj).intValue() : 0;
            Optional<BigDecimal> thresholdAmount = tariffThresholdService
                    .calculateApprovedAmount(interventionCode, los, providerLevel, benefit);
            if (thresholdAmount.isPresent()) {
                if (!isCivilServantClaim) {
                    finalApprovedPrice = thresholdAmount.get().min(requestedAdjAmount);
                }
                log.info("Tariff threshold for ClaimId:{}, Intervention:{}, LOS:{}, thresholdAmount:{}, finalApprovedPrice:{}, civilServant:{}",
                        claimId, interventionCode, los, thresholdAmount.get(), finalApprovedPrice, isCivilServantClaim);
            }

            double totalAmountIncludingCurrent = patientLimit.getUtilisedAmount().longValue() + finalApprovedPrice.doubleValue();
            decisionInputs.put(VAR_INTERVENTION_USE_CNT, patientLimit.getIndividualUtilisedLimit());
            decisionInputs.put(VAR_HOUSE_HOLD_USE_CNT, patientLimit.getHouseholdUtilisedLimit());
            decisionInputs.put(VAR_INDIVIDUAL_USAGE, totalAmountIncludingCurrent);

            // ===========================================================

            // Add adjudication (for approval, adjustment, etc.)
            ClaimResponse.AdjudicationComponent adjudication = new ClaimResponse.AdjudicationComponent();
            boolean isInterventionAutoApproved = false;
            WorkflowService.AdjudicationRecord adjudicationRecord = wfService.autoAdjudicateIntervention(decisionInputs, claim);

            // To Check schemes associated with the interventionCode.
            boolean isShaAndEccifCoverage = benefit.getAppliesToSchemes() != null && BenefitMasterSearchType.CoverageType.TYPE_OF_COVERAGES_ECCIF
                    .contains(benefit.getAppliesToSchemes());
//            boolean isShaAndEccifCoverage = benefitsRepository.existsByInterventionCodeAndAnyAppliesToSchemes(interventionCode,
//                    BenefitMasterSearchType.CoverageType.TYPE_OF_COVERAGES_ECCIF);

            BifurcationService.EccifSegregationResult eccifSegregationResult = BifurcationService.EccifSegregationResult.NO_ACTION;
            // Eccif handling for few funds specially SHA-06 series Items.
            if (isShaAndEccifCoverage) {
                eccifSegregationResult = bifurcationService.segregateECCIFSHAAmount(interventionCode, adjudicationRecord, decisionInputs, claimId,
                        finalApprovedPrice.doubleValue(), use, packageCode, patientCrId, isCivilServantClaim, policyStartDate, policyEndDate,
                        thresholdAmount);
            }

            boolean isRenalRuleFailed = false;

            // TODO: Remove this special case of renal care and move everything to rule engine.
            // Renal care handling for SHA and PMF bifurcation.
            if (isCivilServantClaim && interventionCode.contains("SHA-16")) {
                log.info("Civil Servant : Renal Care for ClaimID: {}, InterventionCode: {}, PatientCRId: {}", claimId, interventionCode, patientCrId);
                LocalDate startServiceDate = claimItem.getServicedPeriodStart();
                try {
                    if (claim.getUse().equals(Claim.Use.PREAUTHORIZATION)) {
                        isRenalRuleFailed = interventionValidationService.validateRenalCarePreAuth(patientCrId, interventionCode, startServiceDate, use, false, policyStartDate, policyEndDate);
                    } else {
                        isRenalRuleFailed = interventionValidationService.validateRenalCare(patientCrId, interventionCode, startServiceDate, use, false, policyStartDate, policyEndDate);
                    }
                } catch (Exception e) {
                    log.error("Civil Servant : Exception Renal Care for ClaimID: {}, InterventionCode: {}, PatientCRId: {}", claimId, interventionCode, patientCrId, e);
                }
            }


            // Rehab LOS-based SHIF limit (used by bifurcation only, does not cap finalApprovedPrice)
            Optional<BigDecimal> rehabLimit = interventionValidationService.getRehabLimit(interventionCode, los);

            // This method is used to segregate the SHA and PMF amounts if item is covered for both.
            if (erpProperties.isBifurcationEnable() && isCivilServantClaim) {
                // To Check schemes associated with the interventionCode.
                boolean isShaAndPMFCoverage = benefit.getAppliesToSchemes() != null && BenefitMasterSearchType.CoverageType.TYPE_OF_COVERAGES_PMSF
                        .contains(benefit.getAppliesToSchemes());
//                boolean isShaAndPMFCoverage = benefitsRepository.existsByInterventionCodeAndAnyAppliesToSchemes(interventionCode, BenefitMasterSearchType.CoverageType.TYPE_OF_COVERAGES_PMSF);
                if (isShaAndPMFCoverage) {
                    bifurcationService.segregatePMFSHAAmount(interventionCode, sequence, adjudicationRecord, decisionInputs, pmfAmountMap,
                            eccifSegregationResult, isRenalRuleFailed, thresholdAmount, finalApprovedPrice, rehabLimit);
                }
            }

            isInterventionAutoApproved = adjudicationRecord.isAutoApproved();
            // When rehab or threshold bifurcation produces PMF, the split is deterministic — auto-approve
            if (pmfAmountMap.get(interventionCode + "_" + sequence) != null
                    && (rehabLimit.isPresent() || thresholdAmount.isPresent())) {
                isInterventionAutoApproved = true;
            }
            if (eccifSegregationResult.eccifApproved() || eccifSegregationResult.limitExceeded()) {
                claimItem.setEccifAmount(eccifSegregationResult.eccifAmount());
                if (eccifSegregationResult.eccifApproved()) {
                    isInterventionAutoApproved = true;
                }
            }

            // SHIF amount from ECCIF segregation result.
            if(eccifSegregationResult.shifApproved()){
                claimItem.setShifAmount(eccifSegregationResult.shifAmount());
                isInterventionAutoApproved = true;
            } else {
                claimItem.setShifAmount(extractedShifAmount(claimItem, pmfAmountMap, interventionCode, sequence, finalApprovedPrice));
            }

            if (pmfAmountMap.get(interventionCode + "_" + sequence) == null) {
                resultMap.put(interventionCode + "_" + sequence, isInterventionAutoApproved);
            }


            if (isInterventionAutoApproved) {
                claimItem.setApprovedPrice(finalApprovedPrice);
                if (eccifSegregationResult.eccifApproved()) {
                    claimItem.setApprovedPrice(finalApprovedPrice);
                }
                claimItem.setStatus(Constants.ClaimLineItemStatus.PASSED);
                claimItem.setJustification("Auto Approved.");
                adjudication.getCategory().setText(Adjudication.BENEFIT.toCode());
                adjudication.getAmount().setValue(finalApprovedPrice);
                adjudication.getReason().setText("Auto Approved.");
            } else {
                adjudication.getCategory().setText("Needs review");
                // Note: Set amount as 0 to ensure that it's not paid.
                adjudication.getAmount().setValue(BigDecimal.valueOf(0));
                adjudication.getReason().setText("Manual review required");
                claimItem.setStatus(Constants.ClaimLineItemStatus.REJECTED);
                claimItem.setJustification(adjudication.getReason().getText());
            }
            responseItem.addAdjudication(adjudication);
            claimResponse.addItem(responseItem);

            if (pmfAmountMap.get(interventionCode + "_" + sequence) != null) {
                claimItem.setPmfItem(true);
            }

            log.info("Auto-Adjudication Process for ClaimID: {}, AdjudicationParams : {}", claimId, new Gson().toJson(decisionInputs));
            claimItem.setAdjudicationParams(new Gson().toJson(decisionInputs));
            if (adjudicationRecord.ruleMap() != null && !adjudicationRecord.ruleMap().isEmpty()) {
                claimItem.setAdjudicationRule(new Gson().toJson(adjudicationRecord.ruleMap()));
            } else {
                // Set and Empty rule here.
                claimItem.setAdjudicationRule(new Gson().toJson("{}"));
            }
            lineItemRepository.save(claimItem);

//            if (finalApprovedPrice.compareTo(requestedAdjAmount) < 0) {
//                String processNote = String.format(
//                        "As per SHA guidelines, the subsequent intervention of the %s package will be approved at 50%% of the requested amount.",
//                        claimItem.getCode()
//                );
//
//                if (claim != null) {
//                    claimsHandlerUtility.updateResource(claim, claimId, claimItem.getClaim().getStatus(), "Claim"); // Update Claim in FHIR
//                }
//                if (claimResponse != null) {
//                    ClaimResponse.NoteComponent claimNote = new ClaimResponse.NoteComponent();
//                    claimNote.setNumber(claimResponse.getProcessNote().size() + 1);
//                    claimNote.setType(Enumerations.NoteType.DISPLAY);
//                    claimNote.setText(processNote);
//                    claimResponse.addProcessNote(claimNote);
//                    claimsHandlerUtility.updateResource(claimResponse, claimId, claimItem.getClaim().getStatus(), "ClaimResponse"); // Update ClaimResponse in FHIR
//                }
//                processNotesService.saveProcessNotes(claimId, claimItem.getClaim().getStatus(), processNote);
//            }

        }
        boolean isCivilServantItemsApproved = true;

        if (!isLiviaClaim) {
            // Civil Servant Items are not adjudicated here. They are sent to Livia for approval.
            isCivilServantItemsApproved = isIsCivilServantItemsApproved(claim, resultMap, iClaim, isCivilServantItemsApproved,
                    claimResponse, interventions, pmfAmountMap);
        }

        log.info("AutoAdjudication Map {} with claim Id : {}", resultMap, claimId);
        // All Adjudications are completed and its Eligible for AutoApproval in Claim Response Object.
        if (resultMap.containsValue(false)) {
            claimResponse.setOutcome(ClaimResponse.RemittanceOutcome.QUEUED);
            claimResponse.setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE);
            isAutoApproved = false;
        } else {
            ClaimResponse.TotalComponent responseTotal = claimResponse.addTotal();
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (ClaimResponse.ItemComponent adjudicationItem : claimResponse.getItem()) {
                totalAmount = totalAmount.add(adjudicationItem.getAdjudication().getFirst().getAmount().getValue());
            }
            responseTotal.setAmount(new Money().setValue(totalAmount).setCurrency(Constants.CURRENCY_SHILLING));
            isAutoApproved = true;
        }

        fhirClient.update().resource(claimResponse).execute();
        return Map.of(VAR_IS_AUTO_APPROVED, isAutoApproved, VAR_IS_CIVIL_SERVICE_APPROVED, isCivilServantItemsApproved);
    }

    /**
     * Calculate SHIF amount for the claim item.
     * @param claimItem
     * @param pmfAmountMap
     * @param interventionCode
     * @param sequence
     * @param finalApprovedPrice
     * @return
     */
    private BigDecimal extractedShifAmount(ClaimLineItem claimItem, Map<String, BigDecimal> pmfAmountMap, String interventionCode,
                                           int sequence, BigDecimal finalApprovedPrice) {
        BigDecimal eccifAmount = BigDecimal.ZERO;
        BigDecimal pmfAmount = BigDecimal.ZERO;
        if (claimItem.getEccifAmount() != null) {
            eccifAmount = claimItem.getEccifAmount();
        }
        if (pmfAmountMap.get(interventionCode + "_" + sequence) != null) {
            pmfAmount = pmfAmountMap.get(interventionCode + "_" + sequence);
        }
        return finalApprovedPrice.subtract(eccifAmount.add(pmfAmount));
    }

    /**
     * Checks if Livia approves the Civil Servant items.
     * If approved, update the claim response and line items accordingly.
     * @param claim The claim being processed.
     * @param resultMap A map to store the approval status of each item.
     * @param iClaim The InsuranceClaim object associated with the claim.
     * @param isCivilServantItemsApproved Flag indicating if civil servant items are approved.
     * @param claimResponse The claim response to be updated.
     * @param interventions List of interventions for adjudication.
     * @param pmfAmountMap Map containing PMF amounts for items.
     * @return true if all civil servant items are approved, false otherwise.
     */
    private boolean isIsCivilServantItemsApproved(Claim claim, Map<String, Boolean> resultMap, InsuranceClaim iClaim,
                                                  boolean isCivilServantItemsApproved, ClaimResponse claimResponse,
                                                  List<Map<String, Object>> interventions, Map<String, BigDecimal> pmfAmountMap)
    {
        List<ClaimLineItem> civilServantItems = new ArrayList<>();
        for (Map<String, Object> adjudicationInputs : interventions) {
            int sequence = (int) adjudicationInputs.get(VAR_SEQUENCE);
            ClaimLineItem claimItem = lineItemRepository.findByClaimAndSequence(iClaim, sequence).get().getFirst();
            if (claimItem.isPmfItem()) {
                if (!pmfAmountMap.isEmpty() && pmfAmountMap.get(claimItem.getCode() + "_" + claimItem.getSequence()) != null) {
                    claimItem.setPmfAmount(pmfAmountMap.get(claimItem.getCode() + "_" + claimItem.getSequence()));
                } else {
                    claimItem.setPmfAmount(claimItem.getNetValue());
                }
                civilServantItems.add(claimItem);
            }
        }
        if (!resultMap.containsValue(false) && !civilServantItems.isEmpty()) {
            if (claim.getUse().equals(Claim.Use.PREAUTHORIZATION)) {
                isCivilServantItemsApproved = liviaClaimService.verifyLiviaClaim(claim, civilServantItems);
            } else {
                isCivilServantItemsApproved = liviaClaimService.submitLiviaClaim(claim, civilServantItems);
            }
            for (ClaimLineItem item : civilServantItems) {
                ClaimResponse.ItemComponent responseItem = new ClaimResponse.ItemComponent();
                responseItem.setItemSequence(item.getSequence());
                ClaimResponse.AdjudicationComponent adjudication = new ClaimResponse.AdjudicationComponent();
                resultMap.put(item.getCode() + "_" + item.getSequence(), isCivilServantItemsApproved);
                if (isCivilServantItemsApproved) {
                    adjudication.getCategory().setText(Adjudication.BENEFIT.toCode());
                    adjudication.getAmount().setValue(item.getNetValue());
                    adjudication.getReason().setText("Auto Approved.");
                    item.setApprovedPrice(item.getNetValue());
                    item.setStatus(Constants.ClaimLineItemStatus.PASSED);
                    item.setJustification(adjudication.getReason().getText());
                } else {
                    adjudication.getCategory().setText("Needs review");
                    // Note: Set amount as 0 to ensure that it's not paid.
                    adjudication.getAmount().setValue(BigDecimal.valueOf(0));
                    adjudication.getReason().setText("Manual review required");
                    item.setStatus(Constants.ClaimLineItemStatus.REJECTED);
                    item.setJustification(adjudication.getReason().getText());
                }
                lineItemRepository.save(item);
                responseItem.addAdjudication(adjudication);
                claimResponse.addItem(responseItem);
            }
        }
        return isCivilServantItemsApproved;
    }

    /**
     * Processes the initializer for a claim and sets relevant variables.
     * @param claimId The ID of the claim to process.
     * @param workFlowParams The map to store processed variables.
     */
    public void processInitializer(String claimId, Map<String, Object> workFlowParams) {
        log.info("Initializing process variables for claimId: {}", claimId);
        Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
        InsuranceClaim iClaim = claimsRepository.findByExternalId(claimId).get().getFirst();
        boolean isEfwaCheckEnabled = false;
        if (Claim.Use.CLAIM.equals(claim.getUse())) {
            if (aiProperties.isEnable() && iClaim.getProvider().getEfwaCheckEnabled()) {
                isEfwaCheckEnabled = claim.getItem().stream()
                        .anyMatch(item -> benefitsRepository.existsByInterventionCodeAndEfwaCheckEnabledIsTrue(
                                item.getProductOrService().getCoding().getFirst().getCode()));
            }
        } else if (Claim.Use.PREAUTHORIZATION.equals(claim.getUse())) {
            log.info("-------is ai preauth enabled: {}", aiProperties.isPreauthAiEnable());
            if (aiProperties.isPreauthAiEnable() && Boolean.TRUE.equals(iClaim.getProvider().getPreauthEfwaEnabled())) {
                isEfwaCheckEnabled = claim.getItem().stream()
                        .anyMatch(item -> benefitsRepository.existsByInterventionCodeAndPreauthEfwaEnabledIsTrue(
                                item.getProductOrService().getCoding().getFirst().getCode()));
                log.info("-----------preauth efwa enable-- {}", isEfwaCheckEnabled);
            }
        }
        workFlowParams.put(VAR_IS_EFWA_ENABLED, isEfwaCheckEnabled);
        workFlowParams.put(VAR_BUNDLE_ID, iClaim.getBundleId());
        workFlowParams.put(VAR_IS_PHC_CLAIM, iClaim.isPHCClaim());
        workFlowParams.put(VAR_SUPPORT_DOC_PRESENT, claim.hasSupportingInfo());

        log.info("Completed Variables Initialization for claimId: {}", claimId);
    }

    /**
     * Checks if EFWA (AI) eligibility is enabled for the given claim.
     * This is extracted from processInitializer and focused only on setting
     * isEfwaEnabled.
     *
     * @param claimId        The ID of the claim to check.
     * @param workFlowParams The map to store the EFWA eligibility result.
     */
    public void checkEfwaEligibility(String claimId, Map<String, Object> workFlowParams) {
        log.info("Checking EFWA eligibility for claimId: {}", claimId);
        Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
        InsuranceClaim iClaim = claimsRepository.findByExternalId(claimId).get().getFirst();
        boolean isEfwaCheckEnabled = false;

        if (Claim.Use.PREAUTHORIZATION.equals(claim.getUse())) {
            log.info("-------is ai preauth enabled: {}", aiProperties.isPreauthAiEnable());
            if (aiProperties.isPreauthAiEnable()
                    && Boolean.TRUE.equals(iClaim.getProvider().getPreauthEfwaEnabled())) {
                isEfwaCheckEnabled = claim.getItem().stream()
                        .anyMatch(item -> benefitsRepository.existsByInterventionCodeAndPreauthEfwaEnabledIsTrue(
                                item.getProductOrService().getCoding().getFirst().getCode()));
                log.info("-----------preauth efwa enable-- {}", isEfwaCheckEnabled);
            }
        } else if (Claim.Use.CLAIM.equals(claim.getUse())) {
            if (aiProperties.isEnable() && iClaim.getProvider().getEfwaCheckEnabled()) {
                isEfwaCheckEnabled = claim.getItem().stream()
                        .anyMatch(item -> benefitsRepository.existsByInterventionCodeAndEfwaCheckEnabledIsTrue(
                                item.getProductOrService().getCoding().getFirst().getCode()));
            }
        }

        workFlowParams.put(VAR_IS_EFWA_ENABLED, isEfwaCheckEnabled);
        log.info("EFWA eligibility for claimId: {} is {}", claimId, isEfwaCheckEnabled);
    }

    private String extractId(String value) {

        log.info("extractId() → Input value: {}", value);

        if (value == null || value.isBlank()) {
            log.warn("extractId() → Input is null or blank, returning null");
            return null;
        }

        // Case: Direct UUID (no slash)
        if (!value.contains("/")) {
            log.info("extractId() → Detected plain ID (no URL). Returning: {}", value);
            return value;
        }

        // Split by slash
        String[] parts = value.split("/");
        log.info("extractId() → Split parts: {}", Arrays.toString(parts));

        // Check for history
        for (int i = 0; i < parts.length; i++) {
            if ("_history".equals(parts[i]) && i > 0) {
                String historyId = parts[i - 1];
                log.info("extractId() → Found _history at index {}. Extracted ID: {}", i, historyId);
                return historyId;
            }
        }
        // No history, return last segment
        String lastPart = parts[parts.length - 1];
        log.info("extractId() → No _history found. Using last segment: {}", lastPart);

        return lastPart;

    }

    public PatientLimit fetchLimits(String patientHouseholdNo, String patientCrId, String use, String packageCode, String interventionCode,
                                    LocalDate policyStartDate, LocalDate policyEndDate, UUID currentClaimId , boolean calculateAmount) {

        List<String> interventionCodes;
        if (!interventionCode.contains(",")) {
            Optional<String> subPackage = benefitsRepository.findSubPackageByInterventionCode(interventionCode);
            log.info("InterventionCode: {}, SubPackage found: {}", interventionCode, subPackage.orElse("NONE"));
            if (subPackage.isPresent()) {
                Optional<List<String>> dbInterventionCodes = benefitsRepository.findInterventionCodesBySubPackage(subPackage.get());
                if (dbInterventionCodes.isPresent() && !dbInterventionCodes.get().isEmpty()) {
                    interventionCodes = dbInterventionCodes.get();
                    log.info("interventionCodes count = {}", interventionCodes.size());
                } else {
                    interventionCodes = List.of(interventionCode);
                    log.warn("No interventionCodes found for subPackage: {}. Falling back to original: {}", subPackage.get(), interventionCode);
                }
            } else {
                log.warn("No subPackage found for interventionCode: {}. Using original interventionCode.", interventionCode);
                interventionCodes = List.of(interventionCode);
            }
        } else {
            interventionCodes = List.of(interventionCode);
        }

        int householdUsageCnt = 1;
        int patientShaUsageCnt = 1;

        log.info("Claim Id : {} , Fetching limits for use {}", currentClaimId, use);
        if (StringUtils.hasText(patientHouseholdNo)) {
            if (use.equals(Constants.Use.PREAUTHORIZATION)) {
                householdUsageCnt = Math.toIntExact(preAuthLimits.countValidOrConsumedPreAuthClaimsOnHouseHold(patientHouseholdNo,
                        APPROVED_EXTENSION_STATES, interventionCodes, use, policyStartDate, policyEndDate, currentClaimId));
                log.info("Claim Id: {}, Use: {}, HouseholdNo: {}, PreAuth household count from repo = {}",
                        currentClaimId, use, patientHouseholdNo, householdUsageCnt);
            } else {
                householdUsageCnt = claimsRepository.countActiveClaimsByHouseholdAndCode(patientHouseholdNo,
                        APPROVED_EXTENSION_STATES, interventionCodes, use, currentClaimId, policyStartDate, policyEndDate);
                log.info("Claim Id: {}, Use: {}, HouseholdNo: {}, Active household count from repo = {}",
                        currentClaimId, use, patientHouseholdNo, householdUsageCnt);
            }
            householdUsageCnt++; // Add this new claim as well into consideration
            log.info("Claim Id: {}, Use: {}, Final householdUsageCnt repo count = {}",
                    currentClaimId, use, householdUsageCnt);
        }

        if (StringUtils.hasText(patientCrId)) {
            if (use.equals(Constants.Use.PREAUTHORIZATION)) {
                patientShaUsageCnt = Math.toIntExact(preAuthLimits.countValidOrConsumedPreAuthClaimsOnCrId(patientCrId,
                        APPROVED_EXTENSION_STATES, interventionCodes, use, policyStartDate, policyEndDate, currentClaimId));
                log.info("Claim Id: {}, Use: {}, PatientCrId: {}, PreAuth patient count from repo = {}",
                        currentClaimId, use, patientCrId, patientShaUsageCnt);
            } else {
                patientShaUsageCnt = claimsRepository.countActiveClaimsByMemberCrIdAndCode(patientCrId,
                        APPROVED_EXTENSION_STATES, interventionCodes, use, currentClaimId, policyStartDate, policyEndDate);
                log.info("Claim Id: {}, Use: {}, PatientCrId: {}, Active patient count from repo = {}",
                        currentClaimId, use, patientCrId, patientShaUsageCnt);
            }
            patientShaUsageCnt++;// Add this new claim as well into consideration
            log.info("Claim Id: {}, Use: {}, Final patientShaUsageCnt repo count = {}",
                    currentClaimId, use, patientShaUsageCnt);
        }

        // ---- Summary log ----
        log.info("Claim Id: {}, Use: {}, Final Counts => HouseholdUsageCnt: {}, PatientShaUsageCnt: {}",
                currentClaimId, use, householdUsageCnt, patientShaUsageCnt);

        // Note: Get the total amount used for the same SHA code and then add the current amount for consideration.
        // This helps business rules to take decision better.
        BigDecimal totalAmountUsedEarlier = BigDecimal.ZERO;

        if(calculateAmount){
            if (use.equals(Constants.Use.PREAUTHORIZATION)) {
                totalAmountUsedEarlier = preAuthLimits.getTotalApprovedAmountWithExpiryAndConsumptionCheck(patientCrId,
                    APPROVED_EXTENSION_STATES, packageCode, use, policyStartDate, policyEndDate, currentClaimId);
            } else {
                totalAmountUsedEarlier = claimsRepository.getTotalAmtOfClaimsByMemberCrIdAndCode(patientCrId,
                    APPROVED_EXTENSION_STATES, packageCode, use, policyStartDate, policyEndDate);
            }
        }

        return new PatientLimit(patientShaUsageCnt, householdUsageCnt, totalAmountUsedEarlier);
    }



    public boolean matchesBlock(String input, String block) {
        if (input == null || block == null) return false;
        return input.trim().toLowerCase().startsWith(block.toLowerCase());
    }

    /**
     * Fetches patient limits with override logic for intervention codes.
     * @param patientHouseholdNo
     * @param patientCrId
     * @param use
     * @param interventionCode comma separated intervention codes or single code
     * @param policyStartDate
     * @param policyEndDate
     * @param currentClaimId
     * @return
     */
    public PatientLimit fetchLimitsOverride(String patientHouseholdNo, String patientCrId, String use, String interventionCode,
                                            LocalDate policyStartDate, LocalDate policyEndDate, UUID currentClaimId) {

        List<String> interventionCodes = Arrays.stream(interventionCode.split(",")).toList();

        int householdUsageCnt = 1;
        int patientShaUsageCnt = 1;

        log.info("Claim Id : {} , Fetching limits for use {}", currentClaimId, use);
        if (StringUtils.hasText(patientHouseholdNo)) {
            if (use.equals(Constants.Use.PREAUTHORIZATION)) {
                householdUsageCnt = Math.toIntExact(preAuthLimits.countValidOrConsumedPreAuthClaimsOnHouseHold(patientHouseholdNo,
                        APPROVED_EXTENSION_STATES, interventionCodes, use, policyStartDate, policyEndDate, currentClaimId));
                log.info("Claim Id: {}, Use: {}, HouseholdNo: {}, PreAuth household count from repo = {}",
                        currentClaimId, use, patientHouseholdNo, householdUsageCnt);
            } else {
                householdUsageCnt = claimsRepository.countActiveClaimsByHouseholdAndCode(patientHouseholdNo,
                        APPROVED_EXTENSION_STATES, interventionCodes, use, currentClaimId, policyStartDate, policyEndDate);
                log.info("Claim Id: {}, Use: {}, HouseholdNo: {}, Active household count from repo = {}",
                        currentClaimId, use, patientHouseholdNo, householdUsageCnt);
            }
            householdUsageCnt++; // Add this new claim as well into consideration
            log.info("Claim Id: {}, Use: {}, Final householdUsageCnt repo count = {}", currentClaimId, use, householdUsageCnt);
        }

        if (StringUtils.hasText(patientCrId)) {
            if (use.equals(Constants.Use.PREAUTHORIZATION)) {
                patientShaUsageCnt = Math.toIntExact(preAuthLimits.countValidOrConsumedPreAuthClaimsOnCrId(patientCrId,
                        APPROVED_EXTENSION_STATES, interventionCodes, use, policyStartDate, policyEndDate, currentClaimId));
                log.info("Claim Id: {}, Use: {}, PatientCrId: {}, PreAuth patient count from repo = {}",
                        currentClaimId, use, patientCrId, patientShaUsageCnt);
            } else {
                patientShaUsageCnt = claimsRepository.countActiveClaimsByMemberCrIdAndCode(patientCrId,
                        APPROVED_EXTENSION_STATES, interventionCodes, use, currentClaimId, policyStartDate, policyEndDate);
                log.info("Claim Id: {}, Use: {}, PatientCrId: {}, Active patient count from repo = {}",
                        currentClaimId, use, patientCrId, patientShaUsageCnt);
            }
            patientShaUsageCnt++;// Add this new claim as well into consideration
            log.info("Claim Id: {}, Use: {}, Final patientShaUsageCnt repo count = {}", currentClaimId, use, patientShaUsageCnt);
        }

        // ---- Summary log ----
        log.info("Claim Id: {}, Use: {}, Final Counts => HouseholdUsageCnt: {}, PatientShaUsageCnt: {}",
                currentClaimId, use, householdUsageCnt, patientShaUsageCnt);

        // Note: Get the total amount used for the same SHA code and then add the current amount for consideration.
        // This helps business rules to take decision better.
        BigDecimal totalAmountUsedEarlier;

        if (use.equals(Constants.Use.PREAUTHORIZATION)) {
            totalAmountUsedEarlier = preAuthLimits.getTotalApprovedAmountWithExpiryAndConsumptionCheck(patientCrId,
                    APPROVED_EXTENSION_STATES, interventionCodes, use, policyStartDate, policyEndDate, currentClaimId);
        } else {
            totalAmountUsedEarlier = claimsRepository.getTotalAmtOfClaimsByMemberCrIdAndCodes(patientCrId,
                    APPROVED_EXTENSION_STATES, interventionCodes, use, policyStartDate, policyEndDate);
        }

        return new PatientLimit(patientShaUsageCnt, householdUsageCnt, totalAmountUsedEarlier);
    }

}
