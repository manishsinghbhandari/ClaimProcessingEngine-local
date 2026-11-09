package io.coredge.health.claims.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.*;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.dto.eligibility.CoveragePeriodDto;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.helpers.FinsprintHelper;
import io.coredge.health.claims.models.PolicyPeriod;
import io.coredge.health.claims.utils.FhirHelper;
import io.coredge.health.claims.utils.RedisIdempotencyService;
import io.coredge.health.claims.config.properties.ApplicationProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static io.coredge.health.claims.exception.models.ErrorCode.BAD_REQUEST;
import static io.coredge.health.claims.exception.models.ErrorCode.DUPLICATE_CLAIM_ID;

@Slf4j
@Service
public class ClaimBundleTransactionalService {

    private final IGenericClient fhirClient;
    private final FinsprintHelper finsprintHelper;
    private final BenefitMasterService benefitMasterService;
    private final ClaimExtensionService extensionService;
    private final InsuranceClaimsService insuranceClaimsService;
    private final LineItemRepository lineItemRepository;
    private final InsuranceClaimsRepository claimsRepository;
    private final ClaimRequestProcessor claimRequestProcessor;
    private final ClaimAttributeService claimAttributeService;
    private final ProviderAttributeService providerAttributeService;
    private final RedisIdempotencyService redisIdempotencyService;
    private final ApplicationProperties.ExternalServices externalServicesProperties;
    private final ApplicationProperties.Livia liviaProperties;
    private final ApplicationProperties.FeatureFlags featureFlagsProperties;

    public ClaimBundleTransactionalService(IGenericClient fhirClient, BenefitMasterService benefitMasterService,
                                           ClaimExtensionService extensionService, InsuranceClaimsService insuranceClaimsService,
                                           LineItemRepository lineItemRepository, InsuranceClaimsRepository claimsRepository,
                                           ClaimRequestProcessor claimRequestProcessor, FinsprintHelper finsprintHelper, ClaimAttributeService claimAttributeService,
                                           ProviderAttributeService providerAttributeService, RedisIdempotencyService redisIdempotencyService,
                                           ApplicationProperties.ExternalServices externalServicesProperties,
                                           ApplicationProperties.Livia liviaProperties,
                                           ApplicationProperties.FeatureFlags featureFlagsProperties) {
        this.fhirClient = fhirClient;
        this.benefitMasterService = benefitMasterService;
        this.extensionService = extensionService;
        this.insuranceClaimsService = insuranceClaimsService;
        this.lineItemRepository = lineItemRepository;
        this.claimsRepository = claimsRepository;
        this.claimRequestProcessor = claimRequestProcessor;
        this.finsprintHelper = finsprintHelper;
        this.claimAttributeService = claimAttributeService;
        this.providerAttributeService = providerAttributeService;
        this.redisIdempotencyService = redisIdempotencyService;
        this.externalServicesProperties = externalServicesProperties;
        this.liviaProperties = liviaProperties;
        this.featureFlagsProperties = featureFlagsProperties;
    }

    @Transactional
    protected ClaimBundleService.ClaimDetailResponce processClaimRequest(Claim claim, Bundle inputBundle, Bundle responseBundle,
                                                                         MessageHeader requestMessageHeader, String externalClaimId,
                                                                         Provider provider, Member member, Map<String, String> preAuthShaCodeMap,
                                                                         PolicyPeriod policyPeriod) {
        if (member == null || provider == null) {
            throw new DataValidationException(" Can't process the Request, Member/Provider is null");
        }

        InsuranceClaim iClaim;
        ClaimResponse claimResponse;
        try {
            // Check for duplicate claim ID if multiple claims are flowing parallel.
            if (insuranceClaimsService.existsByExternalId(claim.getIdPart())) {
                throw new DataValidationException(DUPLICATE_CLAIM_ID, "ClaimId already used earlier.");
            }

            // Validate duplicate claims in the bundle based on intervention daily limits.
            validateDuplicateClaimsInBundle(claim, member);
            validateClaimICDCodes(claim);

            // Mark default response as queued.
            extensionService.updateState(claim, ExtensionStateCodeEnum.PENDING);
            //Store FHIR resources at the end after all validations.
            fhirClient.update().resource(claim).execute();
            //Store FHIR Response resources at the end after all validations.
            claimResponse = generateAndStoreClaimResponse(claim, responseBundle, requestMessageHeader);
            //Note: Add ClaimResponse in Claim Bundle resource.
            log.info("------------ClaimResponse created with====================: {}", claimResponse);
            responseBundle.addEntry(new Bundle.BundleEntryComponent().setResource(claimResponse));
            storeClaimResponseBundle(responseBundle, inputBundle);

            log.info("Processed new Claim/PreAuth request with :"
                            + "RequestBundleID: {}, ResponseBundleID: {}, claimID: {}, claimResponseID: {} ",
                    inputBundle.getIdPart(), responseBundle.getIdPart(), claim.getIdPart(), claimResponse.getId());
        } catch (Exception e) {
            log.warn("Exception while FHIR Response object : {}", e.getMessage());
            throw new BusinessException(BAD_REQUEST, "Exception while updating FHIR object." + e.getMessage());
        }

        // Insert to DB, Claims, Determine if it's a PHC claim
        boolean isPhcClaim = isPhcClaim(claim);

        // Process insurance claim .
        iClaim = claimRequestProcessor.fromClaim(claim, externalClaimId, claimResponse.getIdPart(), inputBundle.getIdPart(),
                provider, member, ExtensionStateCodeEnum.QUEUED);
        iClaim.setPHCClaim(isPhcClaim);

        //Check whether it's a Livia claim and Livia integration is enabled.
        boolean isLiviaClaim = Optional.ofNullable(HealthIdContextHolder.requestSource())
                .map(String::toUpperCase)
                .map(val -> val.equals(Constants.RequestSource.LIVIA.toString()))
                .orElse(false);

        log.info("isLiviaClaim: {}, liviaIntegrationEnabled: {}", isLiviaClaim, liviaProperties.isIntegrationEnable());
        log.info("should process LIVIA Claim: {}", isLiviaClaim && liviaProperties.isIntegrationEnable());
        if (isLiviaClaim && !liviaProperties.isIntegrationEnable()) {
            throw new DataValidationException("Livia integration is disabled.");
        }

        // Save to DB
        Boolean isExist = claimsRepository.existsByClaimId(iClaim.getClaimId()).orElse(false);
        if (isExist) {
            log.info("Claim with ClaimID: '{}' already exists️ in db, Status '{}'.proceeding with callbacks as its already processed.", externalClaimId, iClaim.getStatus().name());
            Optional<List<InsuranceClaim>> existingClaim = claimsRepository.findByClaimId(iClaim.getClaimId());
            return new ClaimBundleService.ClaimDetailResponce(existingClaim.get().getFirst(), claimResponse);
        }
        log.info("Inserting ClaimID {} in DB.", iClaim.getClaimId());
        // Use PolicyPeriod
        if (policyPeriod != null && policyPeriod.getStartDate() != null && policyPeriod.getEndDate() != null) {
            iClaim.setPolicyStartDate(policyPeriod.getStartDate().atStartOfDay());
            iClaim.setPolicyEndDate(policyPeriod.getEndDate().atStartOfDay());
        }
        insuranceClaimsService.save(iClaim);
        if (policyPeriod != null) {
            log.info("Policy Period for claimId {} : Coverage Start: {}, Coverage End: {}",
                    iClaim.getClaimId(), policyPeriod.getStartDate(), policyPeriod.getEndDate());
            claimAttributeService.createOrUpdateClaimAttribute(
                    iClaim.getClaimId(),
                    null,
                    policyPeriod.getCoverageType(),
                    policyPeriod.getEmployer(),
                    policyPeriod.getJobGroup()
            );
        }

        // Claim Line items
        InsuranceClaim finalIClaim = iClaim;
        List<ClaimLineItem> claimLineItems = claim.getItem().stream().map(item -> {
            String code = item.getProductOrService().getCoding().getFirst().getCode();
            String preAuthRefId = preAuthShaCodeMap.get(code);
            BenefitsMaster intervention = benefitMasterService
                    .fetchByInterventionCode(item.getProductOrService().getCoding().getFirst().getCode());
            String interventionName = (intervention != null) ? intervention.getInterventionName() : null;
            return ClaimLineItem.fromClaimItem(item, finalIClaim, interventionName, preAuthRefId);
        }).toList();

        for (ClaimLineItem item : claimLineItems) {
            BigDecimal eccifAmount = item.getEccifAmount() != null ? item.getEccifAmount() : BigDecimal.ZERO;
            BigDecimal pmfAmount = item.getPmfAmount() != null ? item.getPmfAmount() : BigDecimal.ZERO;
            BigDecimal netValue = item.getNetValue() != null ? item.getNetValue() : BigDecimal.ZERO;
            if (eccifAmount.add(pmfAmount).compareTo(netValue) > 0) {
                throw new DataValidationException("Sum of ECCIF and PMF amounts exceeds net value for claim: %s".formatted(iClaim.getClaimId()));
            }
        }

        lineItemRepository.saveAll(claimLineItems);
        return new ClaimBundleService.ClaimDetailResponce(finalIClaim, claimResponse);
    }

    private void validateClaimICDCodes(Claim claim) {
        // check if claim has diagnosis codes
        if (!claim.hasDiagnosis() || claim.getDiagnosis().isEmpty()) {
            throw new DataValidationException(BAD_REQUEST, "Claim diagnosis codes are missing.");
        } else {
            for (var diagnosis : claim.getDiagnosis()) {
                if (!diagnosis.hasDiagnosisCodeableConcept() || !diagnosis.getDiagnosisCodeableConcept().hasCoding()
                        || diagnosis.getDiagnosisCodeableConcept().getCoding().isEmpty()) {
                    throw new DataValidationException(BAD_REQUEST, "Claim diagnosis codes are missing.");
                }
                for (var coding : diagnosis.getDiagnosisCodeableConcept().getCoding()) {
                    if (!coding.hasCode() || StringUtils.isEmpty(coding.getCode().trim())) {
                        throw new DataValidationException(BAD_REQUEST, "Claim diagnosis code is missing.");
                    }
                }
            }
        }
    }

    private void validateDuplicateClaimsInBundle(Claim claim, Member member) {

        //search for other claimLineItems with same service period, intervention code and CR id
        claim.getItem().forEach(item -> {
            String code = item.getProductOrService().getCoding().getFirst().getCode();
            BenefitsMaster intervention = benefitMasterService
                    .fetchByInterventionCode(item.getProductOrService().getCoding().getFirst().getCode());
            if (intervention != null) {
                int dailyLimit = intervention.getDailyLimit() != null ? intervention.getDailyLimit() : 0;
                if (dailyLimit > 0) {
                    Date servicedPeriodStart = null;
                    Date sericedPeriodEnd = null;
                    if (item.hasServicedPeriod()) {
                        log.info("Period start: {}, end: {}", item.getServicedPeriod().getStart(), item.getServicedPeriod().getEnd());
                        Period p = item.getServicedPeriod();
                        servicedPeriodStart = p.getStart();
                        sericedPeriodEnd = p.getEnd();
                    } else {
                        throw new DataValidationException(BAD_REQUEST, "Service Period is missing for claim item with code: " + code);
                    }
                    LocalDate serviceDateStart = FhirHelper.convertToLocalDate(servicedPeriodStart);
                    LocalDate serviceDateEnd = FhirHelper.convertToLocalDate(sericedPeriodEnd);

                    // Redis-based duplicate check to prevent race conditions in multi-server
                    // architecture

                    //ToDo: We have removed the validation for same SHACODE, service period
                    String redisKey = String.format("claim_dup:%s:%s:%s:%s:%s:%d",
                            member.getCrId(), code, serviceDateStart, serviceDateEnd, claim.getUse(), item.getSequence());
                    log.info("Checking duplicate claim with Redis key: {}", redisKey);
                    if (!redisIdempotencyService.shouldProcessRequest(redisKey)) {
                        log.warn("Duplicate claim detected via Redis for key: {}", redisKey);
                        throw new DataValidationException(DUPLICATE_CLAIM_ID,
                                "Duplicate claim detected for intervention code %s on %s to %s for member %s."
                                        .formatted(code, serviceDateStart, serviceDateEnd, member.getCrId()));
                    } else {
                        log.info("Claim is unique via Redis for key: {}", redisKey);
                    }

                    // Secondary DB check for claims that were processed before Redis check was
                    // added
                    int existingClaimsCount = lineItemRepository
                            .countByMemberAndInterventionCodeAndServicedPeriodStartAndServicedPeriodEndAndSequence(
                                    member.getCrId(), code, serviceDateStart, serviceDateEnd, item.getSequence());
                    if (existingClaimsCount >= dailyLimit) {
                        throw new DataValidationException(DUPLICATE_CLAIM_ID,
                                "Duplicate claim detected for intervention code %s on %s to %s exceeding limit of %d for member %s sequence %d."
                                        .formatted(code, serviceDateStart, serviceDateEnd, dailyLimit, member.getCrId(), item.getSequence()));
                    } else {
                        log.info("Claim is unique via DB {}", existingClaimsCount);
                    }
                }
            }
        });
    }

    /**
     * Generates and stores a ClaimResponse for a given Claim, updates the response
     * bundle and optionally updates the MessageHeader with the corresponding response event.
     *
     * @param claim                The claim object for which a ClaimResponse is generated or retrieved.
     * @param responseBundle       The response bundle to be updated with ClaimResponse if required.
     * @param requestMessageHeader The MessageHeader received in the request that will
     *                             be updated with the response event, if provided.
     * @return The generated or retrieved ClaimResponse object.
     */
    private ClaimResponse generateAndStoreClaimResponse(Claim claim, Bundle responseBundle, MessageHeader requestMessageHeader) {

        ClaimResponse claimResponse = null;
        Bundle bundle = fhirClient.search()
                .forResource(ClaimResponse.class)
                .where(ClaimResponse.REQUEST.hasId(claim.getIdPart())).count(50) // Searching by claim ID
                .returnBundle(Bundle.class).execute();

        if (bundle.hasEntry() && !bundle.getEntry().isEmpty()) {
            if (bundle.getEntry().size() > 1) {
                for (Bundle.BundleEntryComponent obj : bundle.getEntry()) {
                    claimResponse = (ClaimResponse) obj.getResource();
                    if (claimResponse.hasItem()) {
                        break;
                    }
                }
            }
            // If no ClaimResponse with Item is found, then return first.
            // Logically, we should never find any claim response with items here.
            if (claimResponse == null) claimResponse = (ClaimResponse) bundle.getEntry().getFirst().getResource();
        }
        //Note: Only create ClaimResponse if not previously created.
        if (claimResponse == null) {
            claimResponse = new ClaimResponse();
            extensionService.updateState(claimResponse, ExtensionStateCodeEnum.PENDING);
            // Set the status and other fields based on the Claim
            claimResponse.setStatus(ClaimResponse.ClaimResponseStatus.ACTIVE);
            claimResponse.setId(UUID.randomUUID().toString()); // Set ID for ClaimResponse
            claimResponse.setRequest(new Reference(claim.getId())); // Reference to the original Claim
            claimResponse.setUse(ClaimResponse.Use.valueOf(claim.getUse().name()));
            claimResponse.setOutcome(ClaimResponse.RemittanceOutcome.QUEUED);
            if (claim.getUse().equals(Claim.Use.PREAUTHORIZATION)) {
                // Create the CodeableConcept for expiry date
                CodeableConcept expiryConcept = new CodeableConcept();
                Coding expiryCoding = new Coding();
                expiryCoding.setSystem(externalServicesProperties.getFhirServerBaseUrl() + "/CodeSystem/claim-expiry");
                expiryCoding.setCode("expiry-date");

                // Set display as expiry date (dynamically or hardcoded)
                expiryCoding.setDisplay(HealthIdContextHolder.preAuthExpiryDate().toString());

                // Add coding to CodeableConcept
                expiryConcept.addCoding(expiryCoding);

                // Create the Extension for expiry date
                Extension expiryExtension = new Extension();
                expiryExtension.setUrl(externalServicesProperties.getFhirServerBaseUrl() + "/StructureDefinition/claim-expiry-extension");
                expiryExtension.setValue(expiryConcept);

                // Add this extension to your resource (example: ClaimResponse)
                claimResponse.addExtension(expiryExtension);
            }
            fhirClient.update().resource(claimResponse).execute();
        }

        if (requestMessageHeader != null) {
            responseBundle.setType(Bundle.BundleType.MESSAGE);    // Prepare Response Bundle back.
            MessageHeader messageHeader = new MessageHeader();    // Update MessageHeader corresponding response event
            messageHeader.getEventCoding().setSystem(requestMessageHeader
                    .getEventCoding().getSystem()).setCode("claim-response");
            responseBundle.addEntry(new Bundle.BundleEntryComponent().setResource(messageHeader));
        }

        // Removed: Add the ClaimResponse to the response Bundle
        //responseBundle.addEntry(new Bundle.BundleEntryComponent().setResource(claimResponse));
        return claimResponse;
    }

    private void storeClaimResponseBundle(Bundle responseBundle, Bundle bundle) {
        String bundleId = bundle.getId();
        if (StringUtils.isEmpty(bundleId.trim())) {
            bundleId = "R-" + UUID.randomUUID();
        } else {
            bundleId = "R-" + bundleId;
        }
        responseBundle.setId(bundleId);
        fhirClient.update().resource(responseBundle).execute();
        log.info("ResponseBundle created with id: {}", responseBundle.getId());
    }

    /**
     * Determines if a claim is a PHC (Primary Health Care) claim
     * by checking if any of the claim items match PHC intervention codes.
     *
     * @param claim The claim to check
     * @return true if the claim is a PHC claim, false otherwise
     */
    public boolean isPhcClaim(Claim claim) {
        if (claim == null || !claim.getUse().equals(Claim.Use.CLAIM)) {
            return false;
        }
        return claim.getItem().stream()
                .anyMatch(item -> benefitMasterService.isPhcClaim(item.getProductOrService().getCoding().getFirst().getCode()));
    }

    /**
     * Validate member eligibility with Finsprint for non-PHC claims
     * Uses scheme priority: TSC > POMSF > SHIF
     */
    public PolicyPeriod validateEligibilityWithFinsprint(Member member, String claimId, LocalDate billableStartDate, LocalDate billableEndDate) {
        try {
            CoveragePeriodDto coveragePeriod = finsprintHelper.checkEligibilityFromMember(member, false, billableStartDate, billableEndDate);
            log.info("Member eligibility validated successfully. Scheme: {}, PolicyStart: {}, CoverageEnd: {}",
                    coveragePeriod.getSchemeName(),
                    coveragePeriod.getPolicyStartDate(),
                    coveragePeriod.getCoverageEndDate());
            return new PolicyPeriod(coveragePeriod.getPolicyStartDate(), coveragePeriod.getCoverageEndDate(),
                    coveragePeriod.getSchemeName(), coveragePeriod.getEmployer(),
                    coveragePeriod.getJobGroup());
        } catch (BusinessException | IllegalArgumentException ex) {
            log.error("Eligibility validation failed for claim: {}. Error: {}", claimId, ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("Unexpected error during Finsprint eligibility check for claim: {}. Error: {}", claimId, e.getMessage(), e);
            throw new DataValidationException("Error validating eligibility for claim " + claimId);
        }
    }

    /**
     * Validate member eligibility with Finsprint for PHC claims
     * Uses UHC scheme only
     */
    public PolicyPeriod validatePhcEligibilityWithFinsprint(Member member, String claimId, LocalDate billableStartDate, LocalDate billableEndDate) {
        try {
            CoveragePeriodDto coveragePeriod = finsprintHelper.checkEligibilityFromMember(member,true, billableStartDate, billableEndDate);
            log.info("PHC Member eligibility validated successfully. Scheme: {}, PolicyStart: {}, CoverageEnd: {}",
                    coveragePeriod.getSchemeName(),
                    coveragePeriod.getPolicyStartDate(),
                    coveragePeriod.getCoverageEndDate());
            return new PolicyPeriod(coveragePeriod.getPolicyStartDate(), coveragePeriod.getCoverageEndDate(),
                    coveragePeriod.getSchemeName(), coveragePeriod.getEmployer(),
                    coveragePeriod.getJobGroup());
        } catch (BusinessException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Unexpected error during Finsprint PHC eligibility check for claim: {}", claimId);
            throw new DataValidationException("Error validating PHC eligibility for claim " + claimId);
        }
    }
}
