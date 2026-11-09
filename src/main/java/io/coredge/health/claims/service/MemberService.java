package io.coredge.health.claims.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.db.repositories.CivilServantAuditRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.db.repositories.MemberRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.app.DatabaseException;
import io.coredge.health.claims.models.ClaimHistoryDTO;
import io.coredge.health.claims.models.PatientResponse;
import io.coredge.health.claims.models.RuleInputDTO;
import io.coredge.health.claims.models.master.BenefitMasterResponse;
import io.coredge.health.claims.models.master.SuggestionResponse;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

import static io.coredge.health.claims.constants.Constants.APPROVED_EXTENSION_STATES;

@Slf4j
@Service
public class MemberService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String PMF_CLAIM_REJECTION_STATUS = "REJECTED";

    private final String PMF_REASON_PREFIX = "(Civil Servant Portal) ";

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private InsuranceClaimsRepository insuranceClaimsRepository;

    @Autowired
    private PagedResourcesAssembler<PatientResponse> pagedPatientResourcesAssembler;

    @Autowired
    private PagedResourcesAssembler<ClaimHistoryDTO> pagedHistoryResourcesAssembler;

    @Autowired
    private BenefitMasterService benefitMasterService;

    @Autowired
    private RuleProcessingService ruleProcessingService;

    @Autowired
    private CivilServantAuditRepository civilServiceAuditRepository;

    private Optional<List<Member>> getMemberDetails(String crdId) {
        try {
            return memberRepository.findByCrId(crdId);
        } catch (Exception e) {
            throw new DatabaseException(e.getMessage().toUpperCase());
        }
    }

    public Optional<List<Member>> findByCrId(String crId) {
        try {
            return memberRepository.findByCrId(crId);
        } catch (Exception e) {
            throw new DatabaseException(e.getMessage().toUpperCase());
        }
    }

    public Optional<List<Member>> findByNationalId(String nationalId) {
        try {
            return memberRepository.findByNationalId(nationalId);
        } catch (Exception e) {
            throw new DatabaseException(e.getMessage().toUpperCase());
        }
    }

    public Boolean existsByCrId(String crId) {
        try {
            return memberRepository.existsByCrId(crId).orElse(false);
        } catch (Exception e) {
            throw new DatabaseException(e.getMessage().toUpperCase());
        }
    }

    public Optional<Boolean> existsByNationalId(String nationalId) {
        try {
            return Optional.of(memberRepository.findByNationalId(nationalId).isPresent());
        } catch (Exception e) {
            throw new DatabaseException(e.getMessage().toUpperCase());
        }
    }

    private static PatientResponse getMemberDetailsResponse(Member member) {
        PatientResponse response = PatientResponse.builder().build();
        BeanUtils.copyProperties(member, response); // Copy properties
        return response;
    }

    private Optional<Page<Member>> fetchAllMemberDetails(String gender, String status, String name, String identifier, Pageable pageable) {
        return Optional.of(memberRepository.findBenefitsByAllOptionalParams(gender , name, identifier, pageable));
    }

    public PagedModel<EntityModel<PatientResponse>> fetchAllPatientDetailsPage(String gender, String status, String name, String identifier, Pageable pageable) {
        Optional<Page<Member>> allMembers = fetchAllMemberDetails(gender, status, name, identifier, pageable);
        Page<PatientResponse> facilityMasterResponse = allMembers
                .filter(Page::hasContent)
                .map(page -> page.map(MemberService::getMemberDetailsResponse))
                .orElseThrow(DataNotFoundException::new);
        return pagedPatientResourcesAssembler.toModel(facilityMasterResponse);
    }

    @Transactional
    public Member saveToDatabase(Member member) {
        log.info("Processing Patient ID: {}", member.getCrId());
        Optional<List<Member>> optionalMember = findByCrId(member.getCrId().trim());
        if (optionalMember.isPresent() && !optionalMember.get().isEmpty()) {
            // Update the field that doesn't have the value
            log.info("Already found in DB ,Updating Info,  Patient ID: {}", member.getCrId());
            Member memberInDB = optionalMember.get().getFirst();
            UUID id = memberInDB.getId();
            LocalDate startDate = memberInDB.getCoverageStartDate();
            LocalDate endDate = memberInDB.getCoverageEndDate();
            LocalDateTime policyStartDate = memberInDB.getPolicyStartDate();
            LocalDateTime policyEndDate = memberInDB.getPolicyEndDate();
            BeanUtils.copyProperties(member, memberInDB);
            memberInDB.setId(id);
            memberInDB.setCoverageStartDate(startDate);
            memberInDB.setCoverageEndDate(endDate);
            memberInDB.setPolicyStartDate(policyStartDate);
            memberInDB.setPolicyEndDate(policyEndDate);

            return memberRepository.save(memberInDB);
        } else {
            // Save to db if Patient not in DB
            return memberRepository.save(member);
        }
    }

    public CollectionModel<EntityModel<ClaimHistoryDTO>> getPatientIndividualHistory(String id, String shaCode, Pageable pageable) {
        Optional<Page<ClaimLineItem>> lineItems = lineItemRepository.findByClaim_Patient_CrIdAndShaCode(id ,shaCode, pageable);
        Page<ClaimHistoryDTO> history =  lineItems
                .filter(Page::hasContent)
                .map(page -> page.map(lineItem ->  getHistoryResponse(lineItem)))
                .orElseThrow(DataNotFoundException::new);
        return pagedHistoryResourcesAssembler.toModel(history);
    }

    public CollectionModel<EntityModel<ClaimHistoryDTO>> getPatientHouseHoldHistory(String id,Long claimNo , String shaCode, Pageable pageable) {
        Optional<Page<ClaimLineItem>> lineItems = lineItemRepository.findFilteredClaimLineItemsByHouseHold(id , claimNo, shaCode, pageable);

        Page<ClaimHistoryDTO> history =  lineItems
                .filter(Page::hasContent)
                .map(page -> page.map(lineItem ->  getHistoryResponse(lineItem)))
                .orElseThrow(DataNotFoundException::new);
        return pagedHistoryResourcesAssembler.toModel(history);
    }

    /**
     * Get the Claim History Response.
     * @param lineItem
     * @return ClaimHistoryDTO
     */
    public ClaimHistoryDTO getHistoryResponse(ClaimLineItem lineItem) {
        // Map properties to the ClaimHistoryDTO
        ClaimHistoryDTO response = ClaimHistoryDTO.builder().build();
        BeanUtils.copyProperties(lineItem, response);
        try {
            //Combined, the Adj process rule and params
            // Process Adjudication Parameters and Rules
            processAdjudicationParams(response, lineItem);

            // Append PMF Claim Rejection Reason (if applicable)
            appendPmfClaimRejectionReason(response, lineItem);

            // Append Additional Adjudication Reasons
            if (lineItem.getStatus() != null && lineItem.getStatus().equals(Constants.ClaimLineItemStatus.REJECTED)) {
                appendAdditionalReasons(response);
            }
        } catch (JsonProcessingException e) {
            if (StringUtils.isNotEmpty(lineItem.getAdjudicationParams())) {
                try {
                    Map<String, Object> inputsMap = objectMapper.readValue(lineItem.getAdjudicationParams(), new TypeReference<Map<String, Object>>() {
                    });
                    response.setAdjudicationParams(inputsMap);
                    response.setReason("The claim code " + lineItem.getCode() + " could not be processed because no corresponding rule was found in the system.");
                } catch (JsonProcessingException je) {
                    log.error("JsonProcessingException : {}", je.getMessage());
                }
            }
        }
        if (lineItem.getClaim().getUse() != null) {
            response.setClaimType(lineItem.getClaim().getUse());
        }
        response.setClaimId(lineItem.getClaim().getExternalId());
        response.setClaimNumber(lineItem.getClaim().getClaimNumber());
        response.setProviderName(lineItem.getClaim().getProvider().getName());
        response.setPatientName(lineItem.getClaim().getPatient().getName());
        response.setInterventionCode(lineItem.getCode());
        response.setStatus(lineItem.getClaim().getStatus());
        response.setPmfAmount(lineItem.getPmfAmount());
        response.setShifAmount(lineItem.getShifAmount());
        response.setPmfItem(lineItem.isPmfItem());
        response.setEccifAmount(lineItem.getEccifAmount());
        response.setLineItemStatus(lineItem.getStatus());
        if(Objects.nonNull(lineItem.getPreAuthId())) {
            response.setPreAuthId(String.valueOf(lineItem.getPreAuthId()));
        }
//        if (benefitMaster != null) {
//            response.setInterventionCode(benefitMaster.getInterventionCode());
//            response.setInterventionName(benefitMaster.getInterventionName());
//        }
        return response;
    }

    /**
     * Process Adjudication Parameters and Rules.
     * @param response
     * @param lineItem
     * @throws JsonProcessingException
     */
    private void processAdjudicationParams(ClaimHistoryDTO response, ClaimLineItem lineItem) throws JsonProcessingException {
        String rule = lineItem.getAdjudicationRule();
        String params = lineItem.getAdjudicationParams();

        if (StringUtils.isNotEmpty(rule) && StringUtils.isNotEmpty(params)) {
            response.setAdjudicationParams(ruleProcessingService.processAdjudicationParamsRule(lineItem));
        } else if (StringUtils.isNotEmpty(params)) {
            response.setAdjudicationParams(objectMapper.readTree(params));
            response.setReason("The claim code " + lineItem.getCode() + " could not be processed because no corresponding rule was found in the system.");
        }
    }

    /**
     * Append PMF Claim Rejection Reason if applicable.
     * @param response
     * @param lineItem
     */
    private void appendPmfClaimRejectionReason(ClaimHistoryDTO response, ClaimLineItem lineItem) {
        if (lineItem.isPmfItem()) {
            civilServiceAuditRepository.findAllByClaimId(lineItem.getClaim().getClaimId().toString())
                    .stream()
                    .filter(audit -> StringUtils.equalsIgnoreCase(audit.getStatus(), PMF_CLAIM_REJECTION_STATUS))
                    .findFirst()
                    .ifPresent(audit -> response.setReason(PMF_REASON_PREFIX + audit.getResponseMessage()));
        }
    }

    /**
     * Append additional reasons to the ClaimHistoryDTO.
     * @param response
     */
    private void appendAdditionalReasons(ClaimHistoryDTO response) {
        String existingReason = Optional.ofNullable(response.getReason())
                .map(String::trim)
                .orElse("");

        StringBuilder reason = new StringBuilder(existingReason);

        if (!existingReason.isBlank() && !existingReason.endsWith(" | ")) {
            reason.append(" | ");
        }

        extractReasons(response, reason);

        // Remove trailing " | " if only one reason exists
        String finalReason = reason.toString().trim();
        if (finalReason.endsWith(" |")) {
            finalReason = finalReason.substring(0, finalReason.length() - 2).trim();
        }

        response.setReason(finalReason);
    }

    /**
     * Extract reasons from the adjudication parameters.
     * @param dto
     * @param reason
     */
    /**
     * Extract reasons from the adjudication parameters.
     *
     * @param dto
     * @param reason
     */
    private void extractReasons(ClaimHistoryDTO dto, StringBuilder reason) {
        boolean containsCivilServantPortal = reason.toString().contains(PMF_REASON_PREFIX);

        Optional.ofNullable(dto.getAdjudicationParams())
                .filter(Map.class::isInstance)
                .map(params -> (Map<?, RuleInputDTO>) params)
                .ifPresent(resultArray ->
                        resultArray.entrySet().stream()
                                .filter(entry -> !("claimAmount".equals(entry.getKey()) && containsCivilServantPortal)) // Ignore claimAmount only if "(Civil Servant Portal)" exists
                                .map(Map.Entry::getValue)
                                .map(RuleInputDTO::getReason)
                                .filter(Objects::nonNull)  // Prevent NullPointerException
                                .filter(Predicate.not(String::isBlank))
                                .forEach(r -> appendReason(reason, r))
                );
    }

    private void appendReason(StringBuilder reason, String newReason) {
        if (!newReason.isBlank()) {
            if (!reason.isEmpty() && !reason.toString().endsWith(" | ")) {
                reason.append(" | ");
            }
            reason.append(newReason);
        }
    }

    @Cacheable(value = "searchAllPatientCache", key = "#searchValue")
    public List<SuggestionResponse> searchAllPatient(String searchValue) {
        Optional<Page<Member>> response = searchByName(searchValue);
        Page<SuggestionResponse> result = response.filter(Page::hasContent)
                .map(page -> page.map(member -> SuggestionResponse.builder().code(member.getId().toString()).name(member.getName()).details(member.getCrId() != null ? member.getCrId() : "").build()))
                .orElseThrow(DataNotFoundException::new);
        return result.getContent();
    }

    private Optional<Page<Member>> searchByName(String searchKey) {
        try {
            int page = 0;
            int size = 10;
            String sortBy = "name";
            return memberRepository.findDistinctByNameContainingIgnoreCase(searchKey, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, sortBy)));
        } catch (Exception e) {
            throw new DatabaseException();
        }
    }

    public List<ClaimHistoryDTO> getPatientHistoryByHouseHold(String houseHoldId, String claimId) {
            Long excludeClaimId = (claimId != null) ? Long.parseLong(claimId) : null;
            List<ClaimLineItem> result = lineItemRepository.findByClaim_Patient_HouseholdNumber(houseHoldId)
                    .orElseThrow(DataNotFoundException::new);
//        //Fetch the interventionName for the interventionCode
        List<BenefitMasterResponse> benefitMasterResponses = benefitMasterService.fetchBenefitMasterAll();
            return result.stream()
                    .filter(item -> excludeClaimId == null ||
                            result.size() <= 1 ||
                            !item.getClaim().getClaimNumber().equals(excludeClaimId))
                    .map(lineItem -> getHistoryResponse(lineItem))

                    .toList();
    }

    public PatientResponse getPatient(String claimId) {

        UUID claimUUID = UUID.fromString(claimId);
        InsuranceClaim claim = insuranceClaimsRepository.findByClaimId(claimUUID)
                .filter(list -> !list.isEmpty())
                .map(List::getFirst)
                .orElseThrow(() -> new DataNotFoundException("Claim not found for claimId: " + claimId));
        return buildPatientResponse(claim);
    }

    public PatientResponse updatePatient(String patientId, Map<String, Object> patientData) {
        Optional<Member> response = memberRepository.findByCrIdOrShaNumber(patientId , patientId);
        if(response.isPresent()){
            Member member = response.get();
            patientData.forEach((key, value) -> {
                try {
                    Field field = Member.class.getDeclaredField(key);
                    field.setAccessible(true);
                    if ("birthDate".equals(key) && value instanceof String) {
                        field.set(member, FhirHelper.convertStringToDate((String) value));
                    }
                    if ("gender".equals(key) && value instanceof String) {
                        field.set(member, value);
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    log.error("Error updating patient data: {}", e.getMessage());
                }
            });
            return getMemberDetailsResponse(memberRepository.save(member));
        }
        throw new BadRequestException("No patient data found for given patientId :"+ patientId);
    }

    public Optional<List<ClaimLineItem>> findByClaim_Patient_CrId(String use, String patientCrId, List<ExtensionStateCodeEnum> statuses) {
        return lineItemRepository.findByClaim_UseAndClaim_Patient_CrIdAndClaim_StatusIn(use, patientCrId, statuses);
    }

    private PatientResponse buildPatientResponse(InsuranceClaim claim) {
        PatientResponse response = PatientResponse.builder().build();
        BeanUtils.copyProperties(claim.getPatient(), response);
        response.setPolicyStartDate(claim.getPolicyStartDate());
        response.setPolicyEndDate(claim.getPolicyEndDate());

        return response;
    }

}
