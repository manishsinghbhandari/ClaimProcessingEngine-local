package io.coredge.health.claims.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.ClaimPaymentProjection;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.repositories.ProcessNotesRepository;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.ClaimPaymentRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.enums.ClaimType;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.app.DatabaseException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.mapper.ClaimMapper;
import io.coredge.health.claims.models.ClaimHistoryDTO;
import io.coredge.health.claims.models.PaymentDetailResponse;
import io.coredge.health.claims.models.RelatedInfo;
import io.coredge.health.claims.models.claims.ClaimDataResponse;
import io.coredge.health.claims.models.master.UserCountySummaryResponse;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.coredge.health.claims.models.claims.PatientIdType;
import io.coredge.health.claims.utils.ClaimsValidator;
import io.coredge.health.claims.utils.PreAuthLimits;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.coredge.health.claims.constants.Constants.*;
import static io.coredge.health.claims.constants.Constants.Use.PREAUTHORIZATION;

import io.coredge.health.claims.context.HealthIdContextHolder;

@Slf4j
@Service
public class InsuranceClaimsService {

    @Autowired
    private InsuranceClaimsRepository insuranceClaimsRepository;

    @Autowired
    private PagedResourcesAssembler<ClaimDataResponse> pagedResourcesAssembler;

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private FhirContext fhirContext;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ClaimPaymentRepository claimPaymentRepository;

    @Autowired
    private PreAuthLimits preAuthLimits;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ClaimsValidator claimsValidator;

    private static final String COLOUR_CODE_RED = "Red";

    private static final Integer COLOUR_CODE_HOURS = 24;
    private String code;

    @Autowired
    private ClaimBundleLogService claimBundleLogService;

    @Autowired
    private ProcessNotesRepository processNotesRepository;

    @Autowired
    private CountyMasterService countyMasterService;

    /**
     * @param claimEntity
     * @return
     */
    private ClaimDataResponse convertToClaimDataResponse(InsuranceClaim claimEntity) {
        ClaimDataResponse response = new ClaimDataResponse();
        BeanUtils.copyProperties(claimEntity, response); // Copy properties
        if (Objects.nonNull(claimEntity.getPatient())) {
            response.setPatientName(claimEntity.getPatient().getName());
            response.setCrId(claimEntity.getPatient().getCrId());
            response.setNationalId(claimEntity.getPatient().getNationalId());
        }
        if (Objects.nonNull(claimEntity.getProvider())) {
            response.setProviderName(claimEntity.getProvider().getName());
            response.setProviderLevel(claimEntity.getProvider().getLevel());
        }
        LocalDateTime currentTime = LocalDateTime.now();
        if (Duration.between(claimEntity.getCreationDate(), currentTime).toHours() >= COLOUR_CODE_HOURS) {
            response.setColourCode(COLOUR_CODE_RED);
        }
        response.setCreationDate(claimEntity.getCreationDate() != null ? LocalDate.from(claimEntity.getCreationDate()) : claimEntity.getClaimCreatedAt());
        //EPORTAL-528
        Long countOfClaims = claimBundleLogService.CountClaimId(claimEntity.getClaimId());
        response.setCount(countOfClaims);

        // Extract shaCode from claim line items
        lineItemRepository.findByClaimId(claimEntity.getClaimId())
                .flatMap(items -> items.stream().findFirst())
                .ifPresent(item -> response.setShaCode(item.getCode()));

        if(Objects.equals(claimEntity.getUse(), PREAUTHORIZATION) && claimEntity.getStatus().equals(ExtensionStateCodeEnum.APPROVED)) {
            response.setStatus(getRelatedInfoStatus(claimEntity));
        }
        else {
            response.setStatus(claimEntity.getStatus());
        }

        return response;
    }

    private boolean isPreAuthConsumed(UUID preAuthId) {
        if (preAuthId == null) {
            return false;
        }
        List<UUID> consumedPreAuthIds = lineItemRepository.findConsumedPreAuthIds(
                List.of(preAuthId),
                Constants.Use.CLAIM,
                CLAIM_APPROVED_EXTENSION_STATES
        );
        return !consumedPreAuthIds.isEmpty();
    }

    private boolean isPreAuthExpired(LocalDate expiryDate) {
        if (expiryDate == null) {
            return false;
        }
        return LocalDate.now().isAfter(expiryDate);
    }

    public Optional<InsuranceClaim> findById(Long id) {
        return insuranceClaimsRepository.findById(id);
    }


    public InsuranceClaim getByExternalID(String claimID) {
        Optional<List<InsuranceClaim>> claimData = insuranceClaimsRepository.findByExternalId(claimID);
        if (claimData.isPresent() && !claimData.get().isEmpty()) {
            return claimData.get().getFirst();
        } else {
            log.error("ClaimID: '{}' not found", claimID);
            throw new DataNotFoundException();
        }
    }

    public InsuranceClaim findByExternalID(String externalID) {
        return insuranceClaimsRepository.findByExternalId(externalID)
                .filter(list -> !list.isEmpty())
                .map(List::getFirst)
                .orElse(null);
    }

    public InsuranceClaim findByExternalIdAndStatus(String externalID, ExtensionStateCodeEnum status) {
        return insuranceClaimsRepository.findByExternalIdAndStatus(externalID, status).map(List::getFirst).orElse(null);
    }

    public List<ClaimDataResponse> findByClaimID(String claimID) {
        Optional<List<InsuranceClaim>> claimIDResponse = insuranceClaimsRepository.findByClaimId(UUID.fromString(claimID));
        return claimIDResponse.filter(list -> !list.isEmpty()).map(list -> list.stream() // If data is present, stream over the list
                        .map(this::convertToClaimDataResponse) // Process each item
                        .collect(Collectors.toList())) // Collect the results into a list
                .orElseThrow(DataNotFoundException::new);
    }

    // Method to fetch the claims and PreAuths
    public PagedModel<EntityModel<ClaimDataResponse>> fetchAllPreAuthDetails(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String providerName, String code, String crId, String nationalId, String shaNumber, String birthCertificate, String accessCode, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode, String county, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Boolean isPHCClaim = false;
        return fetchAllPerAuthClaimsDetails(patientIdType, patientId, claimNumber, patientName, providerName, crId, nationalId, shaNumber, accessCode, shaCode, status, PREAUTHORIZATION, isPHCClaim, auditFlag, shaPackageCode, county, startDate, endDate, pageable);
    }

    public PagedModel<EntityModel<ClaimDataResponse>> fetchAllClaimsDetails(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String providerName, String code, String crId, String nationalId, String shaNumber, String birthCertificate, String accessCode, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode, String county, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Boolean isPHCClaim = false;
        return fetchAllPerAuthClaimsDetails(patientIdType, patientId, claimNumber, patientName, providerName, crId, nationalId, shaNumber, accessCode, shaCode, status, Use.CLAIM, isPHCClaim, auditFlag, shaPackageCode, county, startDate, endDate, pageable);
    }

    public PagedModel<EntityModel<ClaimDataResponse>> fetchAllUpdatedClaimsDetails(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String providerName, String crId, String nationalId, String shaNumber, String accessCode, String fidCode,String level,String shaCode, String shaPackageCode, String county, ExtensionStateCodeEnum status, String use , LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Boolean isPHCClaim = false;
        return fetchAllPerAuthUpdatedClaimsDetails(patientIdType, patientId, claimNumber, patientName, providerName, crId, nationalId, shaNumber, accessCode, fidCode,level , shaCode, shaPackageCode, county,status,use, isPHCClaim, startDate, endDate, pageable);

    }

    // PHC Related Methods.
    public PagedModel<EntityModel<ClaimDataResponse>> fetchAllClaimsPHC(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String providerName, String code, String crId, String nationalId, String shaNumber, String birthCertificate, String accessCode, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode, String county, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Boolean isPHCClaim = true;
        return fetchAllPerAuthClaimsDetails(patientIdType, patientId, claimNumber, patientName, providerName, crId, nationalId, shaNumber, accessCode, shaCode, status, Use.CLAIM, isPHCClaim, auditFlag, shaPackageCode, county, startDate, endDate, pageable);

    }

    public PagedModel<EntityModel<ClaimDataResponse>> fetchAllPerAuthClaimsDetails(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String providerName, String crId, String nationalId, String shaNumber, String accessCode, String shaCode, ExtensionStateCodeEnum status, String use, Boolean isPHCClaim, Boolean auditFlag, String shaPackageCode, String county, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {

        Optional<Page<InsuranceClaim>> claims = Optional.empty();
        boolean hasSearchParams = Stream.of(patientId, claimNumber, patientName, providerName, crId, nationalId, shaNumber, shaCode, accessCode,shaPackageCode,county).anyMatch(StringUtils::isNotBlank);
        if (hasSearchParams) {
            claims = findAllClaims(patientIdType, patientId, claimNumber, isPHCClaim, patientName, providerName, accessCode, crId, nationalId, shaNumber, Helper.convertToList(Helper.decodeValue(shaCode)), status, use, auditFlag, shaPackageCode, county, startDate, endDate, pageable);
        } else {
                // Apply county restriction using user's assigned counties
                String username = HealthIdContextHolder.userId();
                List<String> counties = getUserCounties(username, auditFlag);
                log.info("User '{}' has access to counties: {}", username, counties);
                boolean hasCounties = counties != null && !counties.isEmpty();
                claims = insuranceClaimsRepository.getClaimsByStatusAndUseAndIsPHCClaimAndCreationDateBetween( status, use, isPHCClaim, auditFlag, startDate, endDate, counties, hasCounties, pageable );
        }
        Page<ClaimDataResponse> responses = claims.filter(Page::hasContent).map(page -> page.map(claim -> convertToClaimDataResponse(claim))).orElseThrow(DataNotFoundException::new);
        return pagedResourcesAssembler.toModel(responses);
    }

    public PagedModel<EntityModel<ClaimDataResponse>> fetchAllPerAuthUpdatedClaimsDetails(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String providerName, String crId, String nationalId, String shaNumber, String accessCode, String fidCode ,String level,String shaCode, String shaPackageCode, String county, ExtensionStateCodeEnum status,String use, Boolean isPHCClaim, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
            GetClaimNoID result = getGetClaimNoID(claimNumber);
            if (patientIdType != null) {
                switch (patientIdType) {
                    case crId -> crId = patientId;
                    case shaNumber -> shaNumber = patientId;
                    case nationalId -> nationalId = patientId;
                }
            }
            Optional<Page<InsuranceClaim>> claims = insuranceClaimsRepository.getUpdatedClaims(result.claimId, isPHCClaim, Helper.decodeValue(providerName), accessCode, Helper.decodeValue(patientName), crId, nationalId, shaNumber, fidCode,level  , shaCode, shaPackageCode, county, status ,result.claimNo ,use, startDate, endDate, ExtensionStateCodeEnum.QUEUED ,pageable);
            if (claims.isEmpty() || claims.get().isEmpty()) {
                throw new DataNotFoundException("No Data found for Resubmit Request");
            }

            Page<ClaimDataResponse> responses = claims.filter(Page::hasContent).map(page -> page.map(this::convertToClaimDataResponse)).orElseThrow(DataNotFoundException::new);
            return pagedResourcesAssembler.toModel(responses);

    }

    // get claim by role
    public PagedModel<EntityModel<ClaimDataResponse>> fetchAllClaimsDetailsByRole(String claimNumber, String patientName, String providerName, String code, String crId, String nationalId, String shaNumber, String birthCertificate, String accessCode, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode, String county, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Boolean isPHCClaim = false;
        return fetchAllPreAuthsClaimsPHCByRole(claimNumber, patientName, providerName, code, crId, nationalId, shaNumber, birthCertificate, accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate, pageable, isPHCClaim);
    }

    // get PHC by role
    public PagedModel<EntityModel<ClaimDataResponse>> fetchAllClaimsPHCByRole(String claimNumber, String patientName, String providerName, String code, String crId, String nationalId, String shaNumber, String birthCertificate, String accessCode, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode, String county, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Boolean isPHCClaim = true;
        return fetchAllPreAuthsClaimsPHCByRole(claimNumber, patientName, providerName, code, crId, nationalId, shaNumber, birthCertificate, accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate, pageable, isPHCClaim);
    }

    @NotNull
    private PagedModel<EntityModel<ClaimDataResponse>> fetchAllPreAuthsClaimsPHCByRole(String claimNumber, String patientName, String providerName, String code, String crId, String nationalId, String shaNumber, String birthCertificate, String accessCode, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode, String county, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable, Boolean isPHCClaim) {
        Optional<Page<InsuranceClaim>> claims = findAllClaimsByRole(claimNumber, isPHCClaim, patientName, providerName, mflFix(code), crId, nationalId, shaNumber, birthCertificate, accessCode, level, Helper.convertToList(Helper.decodeValue(shaCode)), status, Use.CLAIM, auditFlag, shaPackageCode, county, startDate, endDate, pageable);
        Page<ClaimDataResponse> responses = claims
                .filter(Page::hasContent)
                .map(page -> page
                        .map(this::convertToClaimDataResponse)).orElseThrow(DataNotFoundException::new);
        return pagedResourcesAssembler.toModel(responses);
    }

    private Optional<List<InsuranceClaim>> findAll() {
        try {
            return Optional.of(insuranceClaimsRepository.findAll());
        } catch (Exception e) {
            throw new DatabaseException(String.format("##Oops Looks something went wrong with DB buddy: %s", e.getMessage()));
        }
    }

    private Optional<Page<InsuranceClaim>> findAllClaims(PatientIdType patientIdType, String patientId, String claimNumber, Boolean isPHCClaim, String patientName, String providerName, String accessCode, String crId, String nationalId, String shaNumber, List<String> shaCode, ExtensionStateCodeEnum status, String use, Boolean auditFlag, String shaPackageCode, String county, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        try {
            county = Helper.decodeValue(county);
            GetClaimNoID result = getGetClaimNoID(claimNumber);
            if (patientIdType != null) {
                switch (patientIdType) {
                    case crId -> crId = patientId;
                    case shaNumber -> shaNumber = patientId;
                    case nationalId -> nationalId = patientId;
                }
            }

            if (!shaCode.isEmpty()) {
                return insuranceClaimsRepository.findAllClaimsWithAllOptionalParams(shaCode, result.claimId, isPHCClaim, Helper.decodeValue(providerName), accessCode, Helper.decodeValue(patientName), crId, nationalId, shaNumber, result.claimNo, status, use, auditFlag, startDate, endDate, pageable );
            }
            // Fetch user counties and pass to repository for filtering
            String username = HealthIdContextHolder.userId();
            List<String> counties = getUserCounties(username, auditFlag);
            log.info("User '{}' has access to counties: {}", username, counties);
            boolean hasCounties = counties != null && !counties.isEmpty();
            if (shaPackageCode != null || county != null) {
                return insuranceClaimsRepository.findByOptionalParams(result.claimId, isPHCClaim, Helper.decodeValue(providerName), accessCode, Helper.decodeValue(patientName), crId, nationalId, shaNumber, result.claimNo, status, use, auditFlag,counties, hasCounties, shaPackageCode, county, startDate, endDate, pageable );
            }

            return insuranceClaimsRepository.findPreAuthByOptionalParams(
                    result.claimId, isPHCClaim, Helper.decodeValue(providerName), accessCode, Helper.decodeValue(patientName),
                    crId, nationalId, shaNumber, result.claimNo, status, use, auditFlag,
                    counties, hasCounties, startDate, endDate, pageable);
        } catch (Exception e) {
            throw new DatabaseException(String.format("Something went wrong, Please try after sometime : %s", e.getMessage()));
        }
    }

    /**
     * Resolve user's assigned county names. Returns empty list when unrestricted.
     */
    private List<String> getUserCounties(String username, Boolean auditFlag) {
        try {
            log.info(String.valueOf(auditFlag));
            UserCountySummaryResponse summary = countyMasterService.getUserCountyAssignments(username);
            if (summary == null || Boolean.TRUE.equals(summary.getHasUnrestrictedAccess()) || summary.getAssignedCounties() == null || !Boolean.TRUE.equals(auditFlag)) {
                return Collections.emptyList();
            }
            return summary.getAssignedCounties().stream()
                    .map(UserCountySummaryResponse.CountyInfo::getCountyName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            // On any failure, default unrestricted to avoid accidental denial
            log.warn("Failed to resolve user counties for {}: {}", username, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves a paginated list of {@link InsuranceClaim} objects that match the specified SHA codes, status, and use.
     * The method filters claims based on the provided status, use, and ensures that PHC claims are excluded.
     *
     * @param shaCode  A list of SHA codes to filter claims.
     * @param status   The status of the claim to match.
     * @param use      The usage type of the claim.
     * @param pageable The pagination and sorting information.
     * @return An {@link Optional} containing a {@link Page} of filtered {@link InsuranceClaim} objects.
     */
    private Optional<Page<InsuranceClaim>> findByShaCodes(List<String> shaCode, ExtensionStateCodeEnum status, String use, Pageable pageable) {
        List<InsuranceClaim> claims = insuranceClaimsRepository.findFilteredClaimNumbersByCodes(shaCode, status, use, Boolean.FALSE).orElse(Collections.emptyList());
        log.debug("Size of PreAuth-claims: {}", claims.size());
        return Optional.of(Helper.getSortedAndPaginatedList(claims, pageable));
    }


    private Optional<Page<InsuranceClaim>> findAll(Pageable pageable) {
        try {
            return Optional.of(insuranceClaimsRepository.findAll(pageable));
        } catch (Exception e) {
            throw new DatabaseException(String.format("$$Oops Looks DB is not responding : %s", e.getMessage()));
        }
    }

    public Boolean isClaimExistByClaimID(String claimID) {
        Optional<Boolean> isExistByClaimID = insuranceClaimsRepository.existsByClaimId(UUID.fromString(claimID));
        return isExistByClaimID.orElse(false);
    }

    @Transactional
    public void updateClaimsStatus(String claimID, ExtensionStateCodeEnum status) {
        try {
            // get the insurance claim object
            InsuranceClaim insuranceClaims = findByExternalID(claimID);
            if (insuranceClaims != null) {
                claimsValidator.validateClaimStatusBeforeUpdate(insuranceClaims.getStatus(), status , insuranceClaims.getUse() , claimID);
                insuranceClaims.setStatus(status);
                log.info("Insurance ClaimID : {} updated with status : {}", claimID, insuranceClaims.getStatus());
                save(insuranceClaims);
            }
        } catch (Exception e) {
            log.error("Error during the InsuranceClaim Status, Claim ID : {} , error:{}", claimID , e.getMessage());
        }
    }

    public String fetchByClaimIdFhir(String claimId) {
        try {
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();

            ObjectNode objectNode = (ObjectNode) mapper.readTree(fhirContext.newJsonParser().encodeResourceToString(claim));
            if(claim.getUse() == Claim.Use.CLAIM && claim.hasRelated()){
                List<RelatedInfo> relateds = claim.getRelated().stream()
                        .filter(Claim.RelatedClaimComponent::hasClaim)
                        .map(Claim.RelatedClaimComponent::getClaim)
                        .filter(Reference::hasIdentifier)
                        .map(Reference::getIdentifier)
                        .filter(Identifier::hasValue)
                        .map(Identifier::getValue)
                        .map(preAuthId -> getRelatedInfo(preAuthId , true))
                        .filter(Objects::nonNull)
                        .toList();

                objectNode.set("relatedInfo" , mapper.valueToTree(relateds));

            } else if (claim.getUse() == Claim.Use.PREAUTHORIZATION) {

                RelatedInfo relatedInfo = getRelatedInfo(claimId , false);
                objectNode.set("relatedInfo" , mapper.valueToTree(relatedInfo));
            }

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectNode);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private RelatedInfo getRelatedInfo(String preAuthId , boolean isClaim){
        Optional<List<InsuranceClaim>> insuranceClaims = insuranceClaimsRepository.findByClaimId(UUID.fromString(preAuthId));

        if(insuranceClaims.isEmpty() || insuranceClaims.get().isEmpty()){
            return null;
        }
        InsuranceClaim preAuth = insuranceClaims.get().getFirst();

        RelatedInfo relatedInfo = RelatedInfo.builder()
                .preAuthExpiryDate(preAuth.getExpiryDate())
                .preAuthStatus(getRelatedInfoStatus(preAuth))
                .build();

        if(isClaim){
            relatedInfo.setPreAuthRequired(Boolean.TRUE);
            relatedInfo.setPreAuthId(preAuthId);
        }

        return relatedInfo;
    }

    private ExtensionStateCodeEnum getRelatedInfoStatus(InsuranceClaim preAuth){
        if(isPreAuthConsumed(preAuth.getClaimId())){
            return ExtensionStateCodeEnum.CONSUMED;
        }
        else if(isPreAuthExpired(preAuth.getExpiryDate())){
            return ExtensionStateCodeEnum.EXPIRED;
        }
            return preAuth.getStatus();
    }

    public PaymentDetailResponse findPaymentDetailByClaimId(String claimId) {
        List<ClaimPaymentProjection> claimPayments = claimPaymentRepository.findByClaimId(UUID.fromString(claimId));
        if (claimPayments == null || claimPayments.isEmpty()) {
            throw new DataNotFoundException("No Data found for payment detail Request for claimId :"+ claimId);
        } else {
            return ClaimMapper.toPaymentDetailResponse(claimPayments , claimId);
        }
    }

    public void save(InsuranceClaim insuranceClaim) {
        insuranceClaimsRepository.save(insuranceClaim);
    }

    public Boolean existsByExternalId(String idPart) {
        return insuranceClaimsRepository.existsByExternalId(idPart).orElse(false);
    }

    private String mflFix(String mflCode) {
        String result;
        if (mflCode != null && mflCode.contains("MFL-")) {
            result = mflCode.replace("MFL-", "");
        } else {
            result = mflCode;
        }
        return result;
    }

    public List<ClaimHistoryDTO> fetchClaimItems(String claimId) {
        Optional<List<ClaimLineItem>> lineItems = lineItemRepository.findByClaimId(UUID.fromString(claimId));
        //Fetch the interventionName for the interventionCode
        return lineItems.map(items -> items.stream().map(lineItem -> memberService.getHistoryResponse(lineItem)).toList()).orElseThrow(DataNotFoundException::new);
    }

    public List<ClaimLineItem> getClaimItems(String claimId) {
        UUID claimUUID = UUID.fromString(claimId);
        return lineItemRepository.findByClaimId(claimUUID).orElse(Collections.emptyList());
    }

    // get pre-auth by role
    public PagedModel<EntityModel<ClaimDataResponse>> fetchAllPreAuthDetailsByRole(String claimNumber, String patientName, String providerName, String code, String crId, String nationalId, String shaNumber, String birthCertificate, String accessCode, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode, String county, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Boolean isPHCClaim = false;
        Optional<Page<InsuranceClaim>> preAuths = findAllClaimsByRole(claimNumber, isPHCClaim, patientName, providerName, mflFix(code), crId, nationalId, shaNumber, birthCertificate, accessCode, level, Helper.convertToList(Helper.decodeValue(shaCode)), status, PREAUTHORIZATION, auditFlag, shaPackageCode, county, startDate, endDate, pageable);
        Page<ClaimDataResponse> responses = preAuths.filter(Page::hasContent).map(page -> page.map(this::convertToClaimDataResponse)).orElse(Page.empty());
        return pagedResourcesAssembler.toModel(responses);
    }

    private Optional<Page<InsuranceClaim>> findAllClaimsByRole(String claimNumber, Boolean isPHCClaim, String patientName, String providerName, String mflCode, String crId, String nationalId, String shaNumber, String birthCertificate, String accessCode, String level, List<String> shaCode, ExtensionStateCodeEnum status, String use, Boolean auditFlag, String shaPackageCode, String county, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        try {
            GetClaimNoID result = getGetClaimNoID(claimNumber);
            String userId = HealthIdContextHolder.userId();
            String filterCode = StringUtils.isBlank(shaPackageCode)? null : shaPackageCode.toUpperCase();
            if (!shaCode.isEmpty()) {

                if(StringUtils.isBlank(shaPackageCode)) {
                    return insuranceClaimsRepository.findAllClaimsWithAllOptionalParamsByRole(shaCode, result.claimId, isPHCClaim, Helper.decodeValue(providerName), accessCode, Helper.decodeValue(patientName), crId, nationalId, shaNumber, result.claimNo, status, use, auditFlag, startDate, endDate, userId, ClaimType.BucketStatus.PENDING, pageable);
                }
                else {
                    return insuranceClaimsRepository.findAllClaimsWithAllOptionalParamsByRole(shaCode, result.claimId, isPHCClaim, Helper.decodeValue(providerName), accessCode, Helper.decodeValue(patientName), crId, nationalId, shaNumber, result.claimNo, status, use, auditFlag, startDate, endDate, userId, ClaimType.BucketStatus.PENDING, filterCode, pageable);
                }
            }
            if(StringUtils.isBlank(shaPackageCode)) {
                return insuranceClaimsRepository.findPreAuthByRolesOptionalParams(result.claimId(), isPHCClaim, Helper.decodeValue(providerName), mflCode, Helper.decodeValue(patientName), crId, nationalId, shaNumber, birthCertificate, result.claimNo(), accessCode, level, status, use, startDate, endDate, userId, ClaimType.BucketStatus.PENDING, pageable);
            }
            else {
                return insuranceClaimsRepository.findPreAuthByRolesOptionalParams(result.claimId(), isPHCClaim, Helper.decodeValue(providerName), mflCode, Helper.decodeValue(patientName), crId, nationalId, shaNumber, birthCertificate, result.claimNo(), accessCode, level, status, use, startDate, endDate, userId, ClaimType.BucketStatus.PENDING, filterCode, pageable);
            }
        } catch (Exception e) {
            throw new DatabaseException(String.format("Something went wrong, Please try after sometime : %s", e.getMessage()));
        }
    }

    public Optional<List<InsuranceClaim>> getApproveClaims(List<UUID> claimIds){
        return insuranceClaimsRepository.findByClaimIdInAndStatus(claimIds,ExtensionStateCodeEnum.APPROVED);
    }

    private static @NotNull GetClaimNoID getGetClaimNoID(String claimNumber) {
        Long claimNo = null;
        UUID claimId = null;
        if (claimNumber != null && !claimNumber.isEmpty()) {
            if (Helper.isValidLong(claimNumber)) {
                claimNo = Long.valueOf(claimNumber);
            } else if (Helper.isValidUUID(claimNumber)) {
                claimId = UUID.fromString(claimNumber);
            } else {
                ErrorAttribute attribute = ErrorAttribute.builder().key("claimNumber").value(claimNumber).build();
                throw new BadRequestException(attribute);
            }
        }
        return new GetClaimNoID(claimNo, claimId);
    }

    private record GetClaimNoID(Long claimNo, UUID claimId) {

    }

    /**
     * Get Bundle Id by Claim Id from claims table
     * @param claimId
     * @return
     */
    public String getBundleId(String claimId) {
        return insuranceClaimsRepository.findBundleIdByClaimId(UUID.fromString(claimId)).get();
    }

    /**
     * Get Bundle Id by Claim Id from claims table
     * @param claimId
     * @return
     */
    public Optional<List<ClaimLineItem>> getLineItem(String claimId) {
        return lineItemRepository.findByClaimId(UUID.fromString(claimId));
    }



    public boolean verifyAssignedClaim(String claimId, String username) {
        UUID uuidClaimId = UUID.fromString(claimId);
        return insuranceClaimsRepository.existsAssignedClaimForUser(uuidClaimId, username, ClaimType.BucketStatus.PENDING.name());
    }
}

