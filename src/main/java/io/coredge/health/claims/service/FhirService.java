package io.coredge.health.claims.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import io.coredge.health.claims.client.WebHookClient;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.utils.FhirDocumentUtils;
import io.coredge.health.claims.utils.ClaimsHandlerUtility;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.coredge.health.claims.exception.models.ErrorCode.CODE_DATA_NOT_FOUND;

@Slf4j
@Service
public class FhirService {

    @Value("${TERMINOLOGY_SERVER_BASE_URL}")
    private String terminologyServerBaseUrl;

    @Value("${supporting.doc.allowed.per.request:10}")
    private int addDocLimit;

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private FhirContext fhirContext;

    @Autowired
    private PostOfficeService postOfficeService;

    @Autowired
    private ClaimsHandlerUtility claimsHandlerUtility;

    @Autowired
    private InsuranceClaimsRepository insuranceClaimsRepository;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private BenefitMasterService benefitMasterService;

    @Autowired
    private WebHookClient webHookClient;

    @Autowired
    private IParser fhirParser;


    public String fetchByClaimId(String claimId) {
        try {
            Claim claim = fhirClient.read(Claim.class, claimId);
            return fhirContext.newJsonParser().encodeResourceToString(claim);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public String fetchByClaimResponseId(String claimResponseId) {
        try {
            ClaimResponse response = getResourceById(ClaimResponse.class, claimResponseId);
            return fhirContext.newJsonParser().encodeResourceToString(response);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public String fetchByPatientId(String patientId) {
        try {
            Patient response = getResourceById(Patient.class, patientId);
            return fhirContext.newJsonParser().encodeResourceToString(response);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public String fetchByOrganizationId(String mflCode) {
        try {
            Organization response = getOrganizationByMflCode(mflCode);
            return fhirContext.newJsonParser().encodeResourceToString(response);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public ClaimResponse fetchClaimResponseByClaimId(String claimId) {
        return postOfficeService.getClaimResponseByClaimId(claimId, null);
    }

    public String claimCallBack(String claimId) {
        try {
            Optional<List<InsuranceClaim>> optionalClaim = insuranceClaimsRepository.findByExternalId(claimId);
            if (optionalClaim.get().isEmpty()) {
                optionalClaim = insuranceClaimsRepository.findByClaimId(UUID.fromString(claimId));
            }
            if (optionalClaim.get().isEmpty()) {
                throw new RuntimeException("Requested claimId or externalId not found : " + claimId);
            }
            InsuranceClaim iClaim = optionalClaim.get().getFirst();
            ClaimResponse claimResponse = postOfficeService.getClaimResponseByClaimId(iClaim.getExternalId(), iClaim.getClaimResponseId());
            if(claimResponse == null){
                throw new BusinessException(ErrorCode.BUSINESS_EXCEPTION, "ClaimId not found in FHIR DB : " + claimId);
            }
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimId, iClaim.getStatus() );
            log.info("Call Back Success for claimId : {} ", claimId);
            return "Call Back Success";
        } catch (Exception e) {
            log.error("Error while claimCallBack for claimId : {} {} ", claimId, e.getMessage());
            throw new BusinessException(ErrorCode.BUSINESS_EXCEPTION, "Error while claimCallBack for claimId : " + claimId + e.getMessage());
        }
    }



    /**
         * Fetches a FHIR resource by its ID.
         * This generic method allows fetching any type of FHIR resource, such as Patient, ClaimResponse,
         * Organization, or Practitioner, by simply passing the resource's ID and class type.
         *
         * <p>Example usage:
         * <pre>
         * {@code
         *
         * // Fetching a ClaimResponse resource
         * ClaimResponse claimResponse = fhirService.getResourceById(ClaimResponse.class, "claimResponseId");
         *
         * // Fetching a Practitioner resource
         * Practitioner provider = fhirService.getResourceById(Practitioner.class, "providerId");
         * }
         * </pre>
         *
         * @param <T>            The type of the FHIR resource (e.g., Patient, ClaimResponse).
         * @param resourceClass     The class of the FHIR resource.
         * @param resourceId  The ID of the resource to fetch.
         * @return The requested resource, or null if not found.
         * @throws RuntimeException if an error occurs while fetching the resource.
         *
         */
    public <T extends IBaseResource> T getResourceById(Class<T> resourceClass, String resourceId) {
        try {
            T resource = fhirClient.read()
                    .resource(resourceClass)
                    .withId(new IdType(resourceId))
                    .execute();
            return resource;
        } catch (ResourceNotFoundException e) {
            log.info("{} with ID: {} not found.", resourceClass.getSimpleName(), resourceId);
            return null;
        } catch (Exception e) {
            log.error("Error fetching {} by ID: {}", resourceClass.getSimpleName(), resourceId, e);
            throw new RuntimeException("Failed to fetch " + resourceClass.getSimpleName() + " from FHIR server", e);
        }
    }

    public Organization getOrganizationByMflCode(String mflCode) {
        try {
            Bundle results = fhirClient.search()
                    .forResource(Organization.class)
                    .where(Organization.IDENTIFIER.exactly().identifier(mflCode))
                    .returnBundle(Bundle.class)
                    .execute();

            if (results.getEntry().isEmpty()) {
                log.info("No Organization found with MFL_CODE: {}", mflCode);
                return null;
            } else {
                return (Organization) results.getEntryFirstRep().getResource();
            }
        } catch (Exception e) {
            log.error("Error searching for Organization resource with MFL_CODE: {}", mflCode, e);
            throw new RuntimeException("Failed to fetch Organization from FHIR server", e);
        }
    }

    //FHIR Service to get CodeSystems
    public String getAllCodeSystems() {
        Bundle bundleCodeSystem = getAllCodeSystemsBundle();
        return fhirContext.newJsonParser().encodeResourceToString(bundleCodeSystem);
    }

    public Bundle getAllCodeSystemsBundle() {
        Bundle bundle;
        try {
            bundle = fhirClient
                    .search()
                    .forResource(CodeSystem.class)
                    .returnBundle(Bundle.class)
                    .execute();

        } catch (Exception e) {
            log.error("Error fetching CodeSystems", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Error while fetching Code System :");
        }
        return bundle;
    }

    /**
     * Getting a CodeSystem by ID and returning JSON representation
     * @param  codeSystemId e.g. diagnosis-type
     * @return The requested resource, or null if not found.
     */
    public String getCodeSystemById(String codeSystemId) {
        try {
            CodeSystem codeSystem = fhirClient.read()
                    .resource(CodeSystem.class)
                    .withId(codeSystemId)
                    .execute();

            // Convert the CodeSystem to JSON
            return fhirContext.newJsonParser().encodeResourceToString(codeSystem);
        } catch (ResourceNotFoundException e) {
            log.info("CodeSystem with ID: {} not found.", codeSystemId);
            throw new BusinessException(CODE_DATA_NOT_FOUND,"No Resource Found for Code System");
        } catch (Exception e) {
            log.error("Error fetching CodeSystem by ID: {}", codeSystemId, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Error while fetching Code System ");
        }
    }

    /**
     * Updating a CodeSystem and returning JSON representation
     */
    public String createOrUpdateCodeSystem(String id, String codeJson) {
        try {
            // Parse the incoming JSON string into a CodeSystem object using HAPI FHIR's JsonParser
            CodeSystem codeSystem = fhirContext.newJsonParser().parseResource(CodeSystem.class, codeJson);
            // Ensure the resource ID is set correctly
            codeSystem.setId(id);
            CodeSystem result;
            try {
                // Check if the resource already exists
                fhirClient.read()
                        .resource(CodeSystem.class)
                        .withId(id)
                        .execute();
                // If it exists, update it
                result = (CodeSystem) fhirClient.update()
                        .resource(codeSystem)
                        .withId(id)
                        .execute()
                        .getResource();
            } catch (ResourceNotFoundException e) {
                // If it does not exist, create a new resource
                result = (CodeSystem) fhirClient.create()
                        .resource(codeSystem)
                        .execute()
                        .getResource();
            }
            return fhirContext.newJsonParser().encodeResourceToString(result);
        } catch (Exception e) {
            log.error("Invalid JSON format for CodeSystem with ID: {}", id, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid JSON format: " + e.getMessage());
        }
    }

    public @NotNull Object updateSupportingDoc(String claimId, List<Map<String, Object>> newAttachments) {
        if (newAttachments.size() > addDocLimit) {
            throw new BadRequestException(String.format("You can only add up to %s documents per request.", addDocLimit));
        }

        // Validate the request
        FhirDocumentUtils.validateAttachments(newAttachments);
        try {
            // Fetch the existing Claim resource
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
            AtomicInteger currentMaxSequence = new AtomicInteger(claim.getSupportingInfo()
                    .stream().mapToInt(Claim.SupportingInformationComponent::getSequence).max()
                    .orElse(0));

            // Validate Status code of the domain resources.
            FhirDocumentUtils.validateStateCode(claim, terminologyServerBaseUrl);

            List<Map<String, Object>> addedDocuments = new ArrayList<>();
            String systemUrl = terminologyServerBaseUrl + "/CodeSystem/attachment-type";

            // Process and add new attachments
            newAttachments.forEach(newAttachment -> {
                Attachment attachment = FhirDocumentUtils.createAttachment(newAttachment, systemUrl);
                int nextSequence = currentMaxSequence.incrementAndGet(); // Increment and get the next sequence
                claim.addSupportingInfo(FhirDocumentUtils.createSupportingInfo(nextSequence, attachment));

                addedDocuments.add(Map.of("sequence", nextSequence, "code", newAttachment.get("attachmentCode")));
            });

            // Update the Claim resource on the FHIR server
            fhirClient.update().resource(claim).execute();
            return Map.of(
                    "Message", "Documents added successfully",
                    "claimId", claimId,
                    "DocumentsDetails", addedDocuments);
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException(e.getMessage());
        } catch (DataValidationException e) {
            log.error("Error in validating supporting documents: {}", claimId, e);
            throw new DataValidationException("$$"+e.getMessage());
        } catch (Exception e) {
            log.error("Error in adding supporting documents: {}", claimId, e);
            throw new RuntimeException("Error in adding supporting documents:");
        }
    }

    public @NotNull Object removeSupportingDocs(String claimId, List<Integer> sequenceNos) {
        if (sequenceNos == null || sequenceNos.isEmpty()) {
            throw new BadRequestException("Sequence numbers list cannot be null or empty.");
        }

        try {
            // Fetch the existing Claim resource
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();

            // Track removed sequences
            List<Integer> removedSequences = new ArrayList<>();

            // Iterate over the provided sequence numbers and remove them
            sequenceNos.forEach(sequenceNo -> {
                boolean isRemoved = claim.getSupportingInfo().removeIf(info -> info.getSequence() == sequenceNo);
                if (isRemoved) {
                    removedSequences.add(sequenceNo);
                }
            });
            if (removedSequences.isEmpty()) {
                throw new BadRequestException(String.format("No matching documents found for the provided sequence numbers: %s", sequenceNos));
            }
            // Update the Claim resource on the FHIR server
            fhirClient.update().resource(claim).execute();
            return Map.of(
                    "Message", "Documents removed successfully",
                    "claimId", claimId,
                    "removedSequences", removedSequences);

        } catch (ResourceNotFoundException e) {
            throw new BadRequestException(e.getMessage());
        } catch (Exception e) {
            log.error("Error in removing supporting documents: {}", claimId, e);
            throw new RuntimeException("Error in removing supporting documents:");
        }
    }

    public String addLineItemsHistory(String use, int count) {
        syncLineItems(use, count);
        return "Started Syncing of Line Items";
    }

    @Async("virtualThreadExecutor")
    protected void syncLineItems(String use, int count) {
        List<String> existClaimIds = lineItemRepository.findDistinctClaimIds();
        Optional<Page<InsuranceClaim>> response = insuranceClaimsRepository.findAllByUseAndExcludeClaimIds(use, existClaimIds, PageRequest.of(0, count, Sort.by(Helper.getSortDirection("ASC"), "creationDate")));
        log.info("Started Line Items Syncing for found elements : {}", response.get().getContent().size());

        List<String> claimIds = response.filter(Page::hasContent).map(page -> page.getContent().stream().map(InsuranceClaim::getExternalId).toList()).orElse(Collections.emptyList());
        if(!claimIds.isEmpty()){
            // Finding from FHIR.
            Bundle claimBundle = fetchClaimsWithBatchRequest(claimIds);
            // Saving data into our system.
            if (claimBundle != null && !claimBundle.getEntry().isEmpty()) {
                response.filter(Page::hasContent).ifPresent(page -> page.getContent().stream()
                        .forEach(claims -> {
                            claimBundle.getEntry().forEach(entry -> {
                                Claim claim = (Claim) entry.getResource();
                                if (claim.getIdPart().equals(claims.getExternalId()) && claim.hasItem()) {
                                    claim.getItem().forEach(item -> {
                                        BenefitsMaster intervention = benefitMasterService.fetchByInterventionCode(item.getProductOrService().getCoding().getFirst().getCode());
                                        ClaimLineItem lineItem = ClaimLineItem.fromClaimItem(item, claims, intervention.getInterventionName(), null);
                                        if (claims.getStatus().equals(ExtensionStateCodeEnum.APPROVED)) {
                                            lineItem.setJustification("Auto Approved");
                                            lineItem.setStatus(Constants.ClaimLineItemStatus.PASSED);
                                            lineItem.setApprovedPrice(lineItem.getUnitPrice());  // TODO : need to update this value
                                        } else if (claims.getStatus().equals(ExtensionStateCodeEnum.SENT_BACK)) {
                                            lineItem.setJustification("Manual review required");
                                            lineItem.setStatus(Constants.ClaimLineItemStatus.REJECTED);
                                        }
                                        lineItemRepository.save(lineItem);
                                    });
                                }
                            });
                        }));
            }
        }
        log.info("Completed Line Items Syncing Successfully");
    }

    /**
     * Fetch multiple Claims using a batch request.
     *
     * @param claimIds List of Claim IDs.
     * @return A Bundle containing all fetched Claims.
     */
    public Bundle fetchClaimsWithBatchRequest(List<String> claimIds) {
        try {
            // Create a Batch Bundle
            Bundle batchBundle = new Bundle();
            batchBundle.setType(Bundle.BundleType.BATCH);

            // Add individual GET requests for each Claim
            claimIds.forEach(id -> {
                Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
                entry.setRequest(new Bundle.BundleEntryRequestComponent()
                        .setMethod(Bundle.HTTPVerb.GET)
                        .setUrl("Claim/" + id)); // Define the URL for each claim
                batchBundle.addEntry(entry);
            });

            // Execute the batch request
            Bundle responseBundle = fhirClient.transaction()
                    .withBundle(batchBundle)
                    .execute();

            return responseBundle;

        } catch (Exception e) {
            log.error("Error fetching Claims using batch request: {}", claimIds, e.getMessage());
            throw new RuntimeException("Failed to execute batch request for Claims :"+ e.getMessage());
        }
    }

}
