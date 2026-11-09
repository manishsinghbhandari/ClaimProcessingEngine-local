package io.coredge.health.claims.component;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.coredge.health.claims.db.entity.ClaimBundleLogEntity;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.ClaimPreAuthProjection;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.models.claims.ClaimItemProjection;
import io.coredge.health.claims.service.SurveillanceService;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.SubType.*;
import static io.coredge.health.claims.constants.Constants.Use.CLAIM;
import static io.coredge.health.claims.constants.Constants.Use.PREAUTHORIZATION;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;

@Slf4j
@Service
public class ClaimBundleComponent {

    private final IGenericClient fhirClient;
    private final InsuranceClaimsRepository claimsRepository;
    private final LineItemRepository lineItemRepository;
    private final ObjectMapper objectMapper;
    private final BenefitsMasterRepository  benefitsMasterRepository;
    private final SurveillanceService surveillanceService;

    private final LocalDateTime VALIDATION_CHECK_DATE_LIMIT = LocalDateTime.parse("2024-11-20T00:00:00");

    public ClaimBundleComponent(IGenericClient fhirClient, InsuranceClaimsRepository claimsRepository, LineItemRepository lineItemRepository, ObjectMapper objectMapper, BenefitsMasterRepository benefitsMasterRepository, SurveillanceService surveillanceService) {
        this.fhirClient = fhirClient;
        this.claimsRepository = claimsRepository;
        this.lineItemRepository = lineItemRepository;
        this.objectMapper = objectMapper;
        this.benefitsMasterRepository = benefitsMasterRepository;
        this.surveillanceService = surveillanceService;
    }

    public InsuranceClaim resubmitClaimBundle(String claimId, Claim inputClaim, InsuranceClaim insuranceClaim) {
        Claim fhirClaim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
        Optional<List<ClaimLineItem>> claimLineItemOptional = lineItemRepository.findByClaimId(UUID.fromString(claimId));
        List<UUID> preAuthIds = Optional.ofNullable(inputClaim.getRelated()).stream().flatMap(Collection::stream)
                    .filter(Claim.RelatedClaimComponent::hasClaim)
                    .map(Claim.RelatedClaimComponent::getClaim)
                    .filter(Reference::hasIdentifier)
                    .map(Reference::getIdentifier)
                    .filter(Identifier::hasValue)
                    .map(Identifier::getValue)
                    .map(UUID::fromString)
                    .toList();

        List<ClaimPreAuthProjection> preAuthLineItemOptional = lineItemRepository.findByClaimIds(preAuthIds , insuranceClaim.getPatient().getCrId() , insuranceClaim.getProvider().getFidCode() , ExtensionStateCodeEnum.APPROVED, PREAUTHORIZATION);

        Map<String , List<ClaimPreAuthProjection>> preAuthByCode = preAuthLineItemOptional.stream().collect(Collectors.groupingBy(ClaimPreAuthProjection::getCode));

        Map<String , List<ClaimLineItem>> claimLineItemMap = claimLineItemOptional.stream().flatMap(Collection::stream).collect(Collectors.groupingBy(ClaimLineItem::getCode));

        validateResubmitClaimBundle(claimId, inputClaim, insuranceClaim, fhirClaim, claimLineItemMap , preAuthByCode);

        try {
            // Check if claim is in MISSING_DOCUMENTS status - only allow support attachments update
            boolean isMissingDocuments = insuranceClaim.getStatus().equals(ExtensionStateCodeEnum.MISSING_DOCUMENTS);
            
            if (isMissingDocuments) {
                log.info("Claim {} is in MISSING_DOCUMENTS status - only updating support attachments", claimId);
                // validate if support attachments are present
                if (!inputClaim.hasSupportingInfo() || inputClaim.getSupportingInfo().isEmpty()) {
                    throw new BadRequestException(String.format("No Supporting Information found in Claim Resubmit Request for id %s", claimId));
                }
                //ONLY Support Attachments are permitted for a claim in MISSING_DOCUMENTS state
                List<Claim.SupportingInformationComponent> supportingInfo = inputClaim.getSupportingInfo();
                if (Objects.nonNull(supportingInfo) && !supportingInfo.isEmpty()) {
                    supportingInfo.forEach(fhirClaim::addSupportingInfo);
                    log.info("Added {} support attachments to claim {}", supportingInfo.size(), claimId);
                    fhirClient.update().resource(fhirClaim).execute();
                    log.info("Updated FHIR claim {} with support attachments", claimId);
                }
                surveillanceService.handleResubmittedMissingDocumentsClaim(insuranceClaim, claimId);
            } else {
                // Full update flow for non-MISSING_DOCUMENTS claims
                log.info("Claim {} is in {} status - performing full update", claimId, insuranceClaim.getStatus());
                //Stored Related after checks
                if (Objects.nonNull(inputClaim.getRelated()) && !inputClaim.getRelated().isEmpty()) {
                    inputClaim.getRelated().forEach(fhirClaim::addRelated);
                }
                //Added Support Attachments
                List<Claim.SupportingInformationComponent> supportingInfo = inputClaim.getSupportingInfo();
                if (Objects.nonNull(supportingInfo) && !supportingInfo.isEmpty()) {
                    supportingInfo.forEach(fhirClaim::addSupportingInfo);
                }
                //Updated Sub
                if (Objects.nonNull(inputClaim.getSubType())) {
                    fhirClaim.setSubType(inputClaim.getSubType());
                }
                //Update Total Amount
                insuranceClaim.setClaimedTotal(inputClaim.getTotal().getValue());
                fhirClaim.getTotal().setValue(inputClaim.getTotal().getValue());
                //Update Line Item NetAmount
                if (claimLineItemOptional.isPresent() && !claimLineItemOptional.get().isEmpty()) {
                    Map<String ,List<ClaimLineItem>> insuranceItemAmountMap = claimLineItemOptional.get().stream().collect(Collectors.groupingBy(ClaimLineItem::getCode));
                    Map<String, List<Claim.ItemComponent>> requesttemAmountMap = inputClaim.getItem().stream()
                            .collect(Collectors.groupingBy(
                                    data -> data.getProductOrService().getCoding().getFirst().getCode()
                            ));


                    Map<String, List<Claim.ItemComponent>> fhirItemMap = fhirClaim.getItem().stream().collect(Collectors.groupingBy(
                            data -> data.getProductOrService().getCoding().getFirst().getCode()
                    ));

                    for (String key : insuranceItemAmountMap.keySet()) {
                        if (requesttemAmountMap.containsKey(key)) {
                            List<ClaimLineItem> insuranceItem = insuranceItemAmountMap.get(key);
                            List<Claim.ItemComponent> requestItem = requesttemAmountMap.get(key);
                            compareClaimLineItems(insuranceItem, requestItem,key);
                        }
                    }


                    requesttemAmountMap.forEach((key, value) -> {
                        value.stream().forEach(requestItem -> {
                            if(!insuranceItemAmountMap.containsKey(key) || !fhirItemMap.containsKey(key)) {
                                throw new BadRequestException(String.format("Missing Requested Code %s in Previously raised Claim", key));
                            }
                            Optional<ClaimLineItem> claimLineItem = insuranceItemAmountMap.get(key).stream()
                                    .findAny();
                            Optional<Claim.ItemComponent> optionalFhirItem = fhirItemMap.get(key).stream()
                                    .findAny();

                            ClaimLineItem dbItem = claimLineItem.get();
                            Claim.ItemComponent fhirItem = optionalFhirItem.get();

                            if (preAuthByCode.containsKey(dbItem.getCode())) {
                                List<ClaimPreAuthProjection> records = preAuthByCode.get(dbItem.getCode());
                                // Check if claim is exempt from period range validation (contains SHA-19 or PMF-07 packages)
                                boolean isClaimExempt = isClaimExemptFromPeriodRangeValidation(insuranceItemAmountMap.keySet());
                                if(records != null && !records.isEmpty()) {
                                    Optional<UUID> preAuthId = records.stream()
                                            .filter(projection -> checkRange(dbItem, projection, isClaimExempt))
                                            .map(ClaimPreAuthProjection::getClaimId).findFirst();
                                if(preAuthId.isPresent()){
                                    dbItem.setPreAuthId(preAuthId.get());
                                }
                                }
                            }

                            try {
                                updateLineItem(dbItem, inputClaim , fhirItem , requestItem );
                            } catch (JsonProcessingException e) {
                                throw new BadRequestException(e);
                            }
                        });

                    });
                }
                //Updated Period
                fhirClaim.getBillablePeriod().setEnd(inputClaim.getBillablePeriod().getEnd());
                fhirClaim.getBillablePeriod().setStart(inputClaim.getBillablePeriod().getStart());
                insuranceClaim.setDateFrom(FhirHelper.convertToLocalDateTime(inputClaim.getBillablePeriod().getStart()));
                insuranceClaim.setDateTo(FhirHelper.convertToLocalDateTime(inputClaim.getBillablePeriod().getEnd()));
                // Updated Fhir claim model
                fhirClient.update().resource(fhirClaim).execute();
            }

            return insuranceClaim;

        } catch (BadRequestException ex){
            throw ex;
        }catch (Exception e) {
            log.info("Error while processing resubmit request for claim ID : {} , with error : {} ", claimId, e.getMessage());
            throw new BadRequestException(String.format("Error in Processing ReSubmit Request for Claim Id %s", claimId));
        }
    }


    private void validateResubmitClaimBundle(String claimId, Claim claim, InsuranceClaim insuranceClaim, Claim fhrClaim, Map<String , List<ClaimLineItem>> claimLineItemMap ,Map<String , List<ClaimPreAuthProjection>> preAuthByCode) {

        //Claim Type would be Claim only
        if (!claim.hasUse() && !StringUtils.equalsIgnoreCase(claim.getUse().toCode(), CLAIM)) {
            throw new BadRequestException(String.format("Invalid Request Support only for Claim , error for id %s", claimId));
        }

        //Status of InsuranceClaim would be REJECTED, SENT_BACK, or MISSING_DOCUMENTS
        if (!insuranceClaim.getStatus().equals(ExtensionStateCodeEnum.SENT_BACK) && 
            !insuranceClaim.getStatus().equals(ExtensionStateCodeEnum.MISSING_DOCUMENTS)) {
            throw new BadRequestException(String.format("Claim with id %s is not in Sent Back or Missing Documents State", claimId));
        }

        //Patient in ClaimRequest and in InsuranceClaim would be same
        if (!claim.hasPatient() || !claim.getPatient().hasReferenceElement() || !claim.getPatient().getReferenceElement().hasIdPart()) {
            throw new BadRequestException(String.format("Patient Id does not found in Claim Resubmit Request for id %s", claimId));
        } else if (!claim.getPatient().getReferenceElement().getIdPart().equalsIgnoreCase(insuranceClaim.getPatient().getCrId()) && !claim.getPatient().getReferenceElement().getIdPart().equalsIgnoreCase(insuranceClaim.getPatient().getShaNumber())) {
            throw new BadRequestException(String.format("Patient Id does not match for id %s", claimId));
        }

        //Provider of ClaimRequest and in InsuranceClaim would be same
        if (!claim.hasProvider() || !claim.getProvider().hasReferenceElement() || !claim.getProvider().getReferenceElement().hasIdPart()) {
            throw new BadRequestException(String.format("Provider Id does not found in Claim Resubmit Request for id %s", claimId));
        }
        if (!StringUtils.equalsIgnoreCase(claim.getProvider().getReferenceElement().getIdPart(), insuranceClaim.getProvider().getFidCode())) {
            throw new BadRequestException(String.format("Provider Id does not match for id %s", claimId));
        }



        //Coverage would be same
        if (validateCoverage(claim , fhrClaim)) {
            throw new BadRequestException(String.format("Coverage does not match for id %s", claimId));
        }

        //Line Item not Present
        if (claim.hasItem() && !claim.getItem().isEmpty() && (claimLineItemMap.isEmpty())) {
            throw new BadRequestException(String.format("Line Item is not present for id %s", claimId));
        }

        // Check if claim is exempt from period range validation (contains SHA-19 or PMF-07 packages)
        boolean isClaimExemptFromPeriodValidation = isClaimExemptFromPeriodRangeValidation(claimLineItemMap.keySet());

        claimLineItemMap.values().stream().flatMap(Collection::stream).forEach(
                item -> {
                    String code = item.getCode();
                    if (preAuthByCode.containsKey(code)) {
                        Optional<UUID> preAuthId = preAuthByCode.get(item.getCode()).stream()
                            .filter(projection -> checkRange(item, projection, isClaimExemptFromPeriodValidation))
                            .map(ClaimPreAuthProjection::getClaimId).findFirst();
                        if(preAuthId.isPresent()){
                            item.setPreAuthId(preAuthId.get());
                        }
                    }
                }
        );

        if (!claimLineItemMap.isEmpty()) {
            List<ClaimItemProjection> requestItems = claim.getItem().stream()
                    .filter(data -> {
                            BigDecimal quantity = data.getQuantity().getValue();
                            BigDecimal unitPrice = data.getUnitPrice().getValue();
                            BigDecimal netValue = data.getNet().getValue();
                            if (unitPrice.multiply(quantity).compareTo(netValue) != 0) {
                                throw new BadRequestException(String.format("Line1 Item net Value is Invalid for id: %s", claimId));
                            }
                            return true;
                        })
                    .map(data -> {
                        Type serviced = data.getServiced();
                        LocalDate[] dates = extractServicedDates(serviced);

                        return new ClaimItemProjection(
                                data.getProductOrService().getCoding().getFirst().getCode(),
                                data.getNet().getValue(),
                                dates[0], // start
                                dates[0], // billing date same as start
                                dates[1]  // end
                        );
                    })
                    .toList();

            log.info("Resubmit Claim: {}, Claim Line Item Map: {}, PreAuth Code Map : {}",
                    claimId, claimLineItemMap.keySet(), preAuthByCode.keySet());

            //Validate if Requested Item have Net Amount greater than stored net amount
            if (requestItems.stream().anyMatch(data -> !claimLineItemMap.containsKey(data.getCode()))) {
                throw new BadRequestException(String.format("No new line Item with code 4 [%s] is allowed for id %s", requestItems.stream().filter(code -> !claimLineItemMap.containsKey(code.getCode())).toList(), claimId));
            } else if (requestItems.stream().map(ClaimItemProjection::getNetValue).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(claim.getTotal().getValue()) != 0) {
                throw new BadRequestException(String.format("Total Amount is not equal to sum of all Net Amount for id %s", claimId));
            } else if (requestItems.stream().anyMatch(
                    data -> {
                        if(!preAuthByCode.containsKey(data.getCode())) {
                            log.info("PreAuth not Found for associated for Line Item Service Period for Claim : {}",data.getCode());
                            return false;
                        }
                            Optional<ClaimPreAuthProjection> preAuth = preAuthByCode.get(data.getCode()).stream()

                                    .filter(projection -> checkRange(data, projection, isClaimExemptFromPeriodValidation))
                                    .findAny();
                            if (preAuth.isEmpty()) {
                                log.info("preAuth not Found for associated for Line Item Service Period for Claim :{} , Code{}",claimId, data.getCode());
                                return false;
                            }

                        BigDecimal netValue = preAuth.get().getNetValue();
                        log.info("PreAuth {} Net Value is {} for id {}", preAuth.get().getClaimId(), netValue, claimId);
                        return data.getNetValue() != null && data.getNetValue().compareTo(netValue) > 0;
                    })) {
                throw new BadRequestException(String.format(
                        "Total Amount in PreAuth is less than Line Item Net Value for id %s", claimId));
            }
        }

        if (insuranceClaim.getClaimedTotal() == null || claim.getTotal().getValue() == null) {
            throw new BadRequestException(String.format("Total Amount is blank for id %s", claimId));
        } else {
            BigDecimal insuranceTotalAmount = insuranceClaim.getClaimedTotal();
            BigDecimal claimTotalAmount = claim.getTotal().getValue();
            if (claimTotalAmount.compareTo(insuranceTotalAmount) > 0) {
                throw new BadRequestException(String.format("Total Amount :%s is greater than Previous Amount %s for id %s", claimTotalAmount, insuranceTotalAmount, claimId));
            }
        }

        //Stored Related after checks
        if (claim.hasRelated() && !claim.getRelated().isEmpty()) {

            List<Claim.RelatedClaimComponent> relatedInfo = claim.getRelated();

            relatedInfo.forEach( relatedClaimComponent -> {

                //Validation check for Accepting more than One PreAuth
                String preAuthId;
                if (relatedClaimComponent.hasClaim() && relatedClaimComponent.getClaim().hasIdentifier() && relatedClaimComponent.getClaim().getIdentifier().hasValue()) {
                    preAuthId = relatedClaimComponent.getClaim().getIdentifier().getValue();
                } else {
                    throw new BadRequestException(String.format("PreAuth id does not exist in Request Payload for id %s", claimId));
                }

                //PreAuth Validation if it is after date Limit
                if (insuranceClaim.getDateFrom().isAfter(VALIDATION_CHECK_DATE_LIMIT)) {
                    Optional<List<InsuranceClaim>> preAuths = claimsRepository.findByClaimId(UUID.fromString(preAuthId));

                    if (preAuths.isEmpty() || preAuths.get().size() > 1) {
                        throw new BadRequestException(String.format("PreAuth does not exist with id %s in Records ", preAuthId));
                    }

                    if (preAuths.get().getFirst().getStatus() != ExtensionStateCodeEnum.APPROVED) {
                        throw new BadRequestException(String.format("PreAuth id %s does not in APPROVED state", claimId));
                    }

                    if (insuranceClaim.getPatient().getId() != preAuths.get().getFirst().getPatient().getId()) {
                        throw new BadRequestException(String.format("Patient Id of Claim  %s and Patient Id of PreAuth %s do not match", claimId, preAuthId));
                    }

                    if (!StringUtils.equals(insuranceClaim.getProvider().getFidCode(), preAuths.get().getFirst().getProvider().getFidCode())) {
                        throw new BadRequestException(String.format("Provider Id of Claim  %s and Provider Id of Pre Auth %s do not match", claimId, preAuthId));
                    }
                }
            });
        }

        //Updated Period Error
        if (claim.getBillablePeriod().getStart().after(claim.getBillablePeriod().getEnd())) {
            throw new BadRequestException(String.format("Billable Period Date Range is invalid for Id %s", claimId));
        }



    }

    //Validate if Coverage is already present in records
    private boolean validateCoverage(Claim claim , Claim fhrClaim) {
        return Optional.of(claim).map(Claim::getInsuranceFirstRep).map(Claim.InsuranceComponent::getCoverage).map(Reference::getId).isPresent() &&
                Optional.of(fhrClaim).map(Claim::getInsuranceFirstRep).map(Claim.InsuranceComponent::getCoverage).map(Reference::getId).isPresent() &&
                !StringUtils.equalsIgnoreCase(claim.getInsuranceFirstRep().getCoverage().getId(), fhrClaim.getInsuranceFirstRep().getCoverage().getId());
    }

    public ClaimBundleLogEntity transformInEntity(String jsonObject, String claimID) {
        Claim fhrClaim = fhirClient.read().resource(Claim.class).withId(claimID).execute();
        ClaimBundleLogEntity logEntity = new ClaimBundleLogEntity();
        logEntity.setClaimId(UUID.fromString(claimID));
        logEntity.setRequestJson(jsonObject);
        logEntity.setVersion(fhrClaim.getMeta().getVersionId());
        return logEntity;
    }

    private void updateLineItem(ClaimLineItem claimLineItem, Claim claim , Claim.ItemComponent fhirItem , Claim.ItemComponent requestItem) throws JsonProcessingException {
        Map<String, Object> adjudicationDecisionInputs = objectMapper.readValue(claimLineItem.getAdjudicationParams(), LinkedHashMap.class);
        if (Objects.nonNull(claim.getRelated()) && !claim.getRelated().isEmpty()) {
            adjudicationDecisionInputs.put(VAR_HAS_PRE_AUTH, true);
        }
        if (Objects.nonNull(claim.getSubType())) {
            String serviceCatagory = ""; // Defaults to inpatient as it can be any.
            if (claim.hasSubType()) {
                serviceCatagory = claim.getSubType().getText();
                String sybType = claim.getSubType().getCoding().getFirst().getCode();
                serviceCatagory = switch (sybType) {
                    case OUTPATIENT -> "outpatient";
                    case INPATIENT -> "inpatient";
                    case EMERGENCY -> "emergency";
                    default -> serviceCatagory;
                };
            }
            adjudicationDecisionInputs.put(VAR_SERVICE_CATEGORY, serviceCatagory);
        }

        BigDecimal individualUsageCnt =BigDecimal.valueOf(Double.parseDouble(adjudicationDecisionInputs.get(VAR_INDIVIDUAL_USAGE_CNT).toString())).subtract( claimLineItem.getNetValue()).add(claim.getTotal().getValue());
        adjudicationDecisionInputs.put(VAR_INDIVIDUAL_USAGE_CNT, individualUsageCnt.doubleValue());
        adjudicationDecisionInputs.put(VAR_CLAIM_AMT, claim.getTotal().getValue().doubleValue());
        adjudicationDecisionInputs.put(VAR_LOS, FhirHelper.calculateLoS(claim));
        claimLineItem.setAdjudicationParams(new Gson().toJson(adjudicationDecisionInputs));
        claimLineItem.setUnitPrice(requestItem.getUnitPrice().getValue());
        claimLineItem.setApprovedPrice(null);
        claimLineItem.setQuantity(requestItem.getQuantity().getValue().longValue());
        claimLineItem.setNetValue(requestItem.getNet().getValue());
        if(fhirItem !=  null) {
            fhirItem.getUnitPrice().setValue(requestItem.getUnitPrice().getValue());
            fhirItem.getQuantity().setValue(requestItem.getQuantity().getValue());
            fhirItem.getNet().setValue(requestItem.getNet().getValue());
        }
        lineItemRepository.save(claimLineItem);
    }


    // Package prefixes exempt from preauthorisation period range validation
    private static final String SHA_19_PACKAGE_PREFIX = "SHA-19-";
    private static final String PMF_07_PACKAGE_PREFIX = "PMF-07-";

    /**
     * Checks if a code belongs to packages exempt from preauthorisation period range validation.
     * Currently exempts SHA-19 and PMF-07 packages.
     */
    private boolean isExemptPackageCode(String code) {
        if (code == null) {
            return false;
        }
        String upperCode = code.toUpperCase();
        return upperCode.startsWith(SHA_19_PACKAGE_PREFIX) || upperCode.startsWith(PMF_07_PACKAGE_PREFIX);
    }

    /**
     * Checks if any code in the provided collection belongs to exempt packages.
     * If ANY item in the claim has SHA-19-XXX or PMF-07-XXX, the ENTIRE claim is exempt
     * from preauthorisation period range validation.
     */
    public boolean isClaimExemptFromPeriodRangeValidation(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return false;
        }
        boolean exempt = codes.stream().anyMatch(this::isExemptPackageCode);
        if (exempt) {
            log.info("Claim contains exempt package code (SHA-19 or PMF-07) - entire claim exempt from period range validation");
        }
        return exempt;
    }

    private boolean checkRange(ClaimLineItem data, ClaimPreAuthProjection dbData, boolean isClaimExempt) {
        // If claim is exempt (contains SHA-19 or PMF-07 items), skip period range validation for ALL items
        if (isClaimExempt) {
            log.info("Exempting code {} from preauthorisation period range validation (claim-level exemption)", data.getCode());
            return true;
        }

        if(Objects.nonNull(data.getServicedPeriodStart()) && Objects.nonNull(data.getServicedPeriodEnd())) {
            if (dbData.getServicedPeriodStart() == null || dbData.getGetServicedPeriodEnd() == null) {
                return false;
            }

            return !data.getServicedPeriodStart().isBefore(dbData.getServicedPeriodStart())   // claimStartDate >= preAuthStartDate
                    && !data.getServicedPeriodEnd().isAfter(dbData.getGetServicedPeriodEnd());      // claimEndDate <= preAuthEndDate
        }
        else{
            return !data.getServicedDate().equals(dbData.getServiceDate());
        }
    }

    private boolean checkRange(ClaimItemProjection data, ClaimPreAuthProjection dbData, boolean isClaimExempt) {
        // If claim is exempt (contains SHA-19 or PMF-07 items), skip period range validation for ALL items
        if (isClaimExempt) {
            log.info("Exempting code {} from preauthorisation period range validation (claim-level exemption)", data.getCode());
            return true;
        }

        if(Objects.nonNull(data.getServicedPeriodStart()) && Objects.nonNull(data.getServicedPeriodEnd())) {
            if (dbData.getServicedPeriodStart() == null || dbData.getGetServicedPeriodEnd() == null) {
                return false;
            }

            return !data.getServicedPeriodStart().isBefore(dbData.getServicedPeriodStart())   // claimStartDate >= preAuthStartDate
                    && !data.getServicedPeriodEnd().isAfter(dbData.getGetServicedPeriodEnd());      // claimEndDate <= preAuthEndDate
        }
        else{
            return !data.getServiceDate().equals(dbData.getServiceDate());
        }
    }

    /*private boolean filterClaimItem(ClaimLineItem item, Claim.ItemComponent requestItem) {

        String claimId = item.getClaim() != null ? item.getClaim().getClaimId().toString() : "N/A";

        if (requestItem.hasServicedPeriod()) {
            log.info("Process start for validation against Claim ID: {}", claimId);

            LocalDate reqStart = convertToLocalDate(requestItem.getServicedPeriod().getStart());
            LocalDate reqEnd = convertToLocalDate(requestItem.getServicedPeriod().getEnd());

            if (item.getServicedPeriodStart() == null) {
                // OLD FLOW
                log.info("Process start for old flow validation against Claim ID: {}", claimId);

                LocalDate servicedDate = convertToLocalDate(requestItem.getServicedPeriod().getStart());

                if (!item.getServicedDate().equals(servicedDate)) {
                    throw new BadRequestException(
                            String.format(
                                    "Resubmitted serviced date [%s] does not match initial serviced date [%s]",
                                    servicedDate, item.getServicedDate()
                            )
                    );
                }

                return requestItem.getNet().getValue().compareTo(item.getNetValue()) <= 0;

            } else {
                // NEW FLOW
                log.info("Process start for new flow validation against Claim ID: {}", claimId);

                if (!item.getServicedPeriodStart().equals(reqStart)) {
                    throw new BadRequestException(
                            String.format(
                                    "Resubmitted serviced period start [%s] does not match initial serviced period start [%s]",
                                    reqStart, item.getServicedPeriodStart()
                            )
                    );
                }

                if (!item.getServicedPeriodEnd().equals(reqEnd)) {
                    throw new BadRequestException(
                            String.format(
                                    "Resubmitted serviced period end [%s] does not match initial serviced period end [%s]",
                                    reqEnd, item.getServicedPeriodEnd()
                            )
                    );
                }

                return requestItem.getNet().getValue().compareTo(item.getNetValue()) <= 0;
            }

        } else {

            LocalDate serviceDate = extractServiceDate(requestItem.getServiced());

            if (!item.getServicedDate().equals(serviceDate)) {
                throw new BadRequestException(
                        String.format(
                                "Resubmitted serviced date [%s] does not match initial serviced date [%s]",
                                serviceDate, item.getServicedDate()
                        )
                );
            }

            return true;
        }
    }*/



    /*private boolean filterClaimItem(Claim.ItemComponent item, Claim.ItemComponent requestItem) {
        if (requestItem.hasServicedPeriod()) {
            if (extractServiceDateFromFhir(item.getServiced()) != null) {
                log.info("Process start for old flow validation from fhir and requested item");
                LocalDate fhirDate = extractServiceDateFromFhir(item.getServiced());
                LocalDate requestedDate = convertToLocalDate(requestItem.getServicedPeriod().getStart());
                boolean dateEquals = Objects.equals(fhirDate, requestedDate);
                log.info("Local Date comparison: {}", dateEquals);
                boolean netIsValid = requestItem.getNet().getValue()
                        .compareTo(item.getNet().getValue()) <= 0;
                log.info("Net Is Valid: {}", netIsValid);
                return dateEquals && netIsValid;
            } else {
                log.info("Process start for new flow validation from fhir and requested item");
                return item.getServicedPeriod().getStart().equals(requestItem.getServicedPeriod().getStart()) &&
                        item.getServicedPeriod().getEnd().equals(requestItem.getServicedPeriod().getEnd()) &&
                        requestItem.getNet().getValue().compareTo(item.getNet().getValue()) <= 0;
            }
        } else {
            LocalDate existingDate = extractServiceDateFromFhir(item.getServiced());
            LocalDate incomingDate = extractServiceDateFromFhir(requestItem.getServiced());
            boolean dateEquals = Objects.equals(existingDate, incomingDate);
            log.info("LocalDate comparison: {}", dateEquals);
            boolean netIsValid = requestItem.getNet().getValue()
                    .compareTo(item.getNet().getValue()) <= 0;
            log.info("NetIsValid: {}", netIsValid);
            return dateEquals && netIsValid;
        }
    }*/

    private LocalDate[] extractServicedDates(Type serviced) {
        if (serviced instanceof DateType dateType) {
            LocalDate date = convertToLocalDate(dateType.getValue());
            return new LocalDate[]{date, date}; // start = end = same date
        }
        if (serviced instanceof Period period) {
            LocalDate start = convertToLocalDate(period.getStart());
            LocalDate end = convertToLocalDate(period.getEnd());
            return new LocalDate[]{start, end};
        }
        throw new BadRequestException("Invalid serviced field: expected Date or Period");
    }


    private LocalDate extractServiceDate(Type serviced) {
        if (serviced instanceof DateType dateType) {
            return dateType.getValue().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }
        return null;
    }

    public static LocalDate convertToLocalDate(Date date) {
        // Convert Date to Instant, then to LocalDate using system's default zone
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private LocalDate extractServiceDateFromFhir(Type serviced) {
        if (serviced == null) {
            return null;
        }
        if (serviced instanceof DateType dateType && dateType.getValue() != null) {
            return dateType.getValue()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }
        log.warn("Unknown FHIR Type for serviced date: {}", serviced.getClass().getName());
        return null;
    }


    private void  compareClaimLineItems(List<ClaimLineItem> insuranceItems, List<Claim.ItemComponent> requestItems, String key) {

        Set<LocalDate> dbServicedDates = new HashSet<>();
        Set<LocalDate> reqServicedDates = new HashSet<>();

        Set<LocalDate> dbPeriodStarts = new HashSet<>();
        Set<LocalDate> reqPeriodStarts = new HashSet<>();

        Set<LocalDate> dbPeriodEnds = new HashSet<>();
        Set<LocalDate> reqPeriodEnds = new HashSet<>();

        for (ClaimLineItem dbItem : insuranceItems) {
            dbServicedDates.add(dbItem.getServicedDate());
            dbPeriodStarts.add(dbItem.getServicedPeriodStart());
            dbPeriodEnds.add(dbItem.getServicedPeriodEnd());
        }
        for (Claim.ItemComponent reqItem : requestItems) {
            LocalDate serviceDate;
            LocalDate periodStart = null;
            LocalDate periodEnd = null;

            if (reqItem.hasServicedPeriod()) {
                periodStart = convertToLocalDate(reqItem.getServicedPeriod().getStart());
                periodEnd = convertToLocalDate(reqItem.getServicedPeriod().getEnd());
                reqPeriodStarts.add(periodStart);
                reqPeriodEnds.add(periodEnd);
                serviceDate = convertToLocalDate(reqItem.getServicedPeriod().getStart());
            } else {
                serviceDate = extractServiceDate(reqItem.getServiced());
            }

            reqServicedDates.add(serviceDate);
        }

        if (!dbServicedDates.isEmpty() && !dbServicedDates.equals(reqServicedDates)) {
            throw new BadRequestException(
                    String.format(
                            "Resubmitted serviced dates %s do not match initial serviced dates %s for code [%s]",
                            reqServicedDates, dbServicedDates, key
                    )
            );
        }

        if (!dbPeriodStarts.isEmpty() && !dbPeriodStarts.equals(reqPeriodStarts)) {
            throw new BadRequestException(
                    String.format(
                            "Resubmitted serviced period start dates %s do not match initial serviced period start dates %s for code [%s]",
                            reqPeriodStarts, dbPeriodStarts, key
                    )
            );
        }

        if (!dbPeriodStarts.isEmpty() && !dbPeriodEnds.equals(reqPeriodEnds)) {
            throw new BadRequestException(
                    String.format(
                            "Resubmitted serviced period end dates %s do not match initial serviced period end dates %s for code [%s]",
                            reqPeriodEnds, dbPeriodEnds, key
                    )
            );
        }
    }


}