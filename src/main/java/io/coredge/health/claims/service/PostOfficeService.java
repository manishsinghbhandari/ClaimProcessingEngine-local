package io.coredge.health.claims.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.ProviderRepository;
import io.coredge.health.claims.exception.app.DataValidationException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import io.coredge.health.claims.utils.FhirHelper;

import static io.coredge.health.claims.constants.Constants.CURRENCY_SHILLING;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PostOfficeService {
  @Autowired
  private IGenericClient fhirClient;

  @Autowired
  private FhirContext fhirContext;

  @Autowired
  private IParser fhirParser;

  @Autowired
  private HealthExchangeService hieService;

  @Autowired
  private WorkflowService wfService;

  @Autowired
  private FhirHelper fhirHelper;

  @Value("${TERMINOLOGY_SERVER_BASE_URL:https://mis.apeiro-digital.com/fhir}")
  private String fhirServerBaseUrl;

  /**
   *  Changes in the Business Flow
   *  1. Validate the Patient Using the HIE Server
   *  2. Patient is Present, Save/Update to Database or Throw Not Found Exception
   */
  public Patient getPatient(String patientRef) {
    Patient patient = null;
    String patientId = patientRef;
    if (patientRef.contains("identifier=")) {
      patientId = patientRef.split("identifier=")[1];
    } else if (patientRef.contains("/")) {
      patientId = patientRef.split("/")[1];
    }
    patient = hieService.fetchFhirPatient("National ID", patientId, true);
    if (patient == null) {
      // Also push to FHIR, if not exist in FHIR SYSTEM
      if (Boolean.FALSE.equals(fhirHelper.isPatientExistsInFhir(patientId))) {
        MethodOutcome responseOutcome = fhirClient.update().resource(patient).execute();
      }
    } else {
      throw new DataValidationException("$$Patient via nationalID Not found in the HIE");
    }
    return patient;
  }

  /**
   * Fetch Patient from PostOffice using any identifier.
   *
   * @param patientRef
   * @return
   */
  public Patient fetchPatientWithref(String patientRef) {
    Patient patient = null;
    try {
      Bundle results =
          fhirClient
              .search()
              .forResource(Patient.class)
              .where(Patient.IDENTIFIER.exactly().identifier(patientRef))
              .returnBundle(Bundle.class)
              .execute();
      if (results.getEntry().isEmpty()) {
        return null; // No patient found with this identifier
      } else {
        // Return the first match (assuming unique identifiers)
        patient = (Patient) results.getEntryFirstRep().getResource();
      }

    } catch (ResourceNotFoundException e) {
      // If the patient is not found, return false
      log.info("Patient with Ref:" + patientRef + " not found.");
    } catch (Exception e) {
      // Handle other possible exceptions (e.g., network issues, invalid server response)
      throw new RuntimeException("Error occurred while checking patient existence", e);
    }
    return patient;
  }

  /**
   * Fetch Any Domain resource from PostOffice using any identifier.
   *
   * @param ref
   * @return
   */
  public <T extends DomainResource> DomainResource findWithIdentifier(Class<T> clazz, String ref) {
    try {
      Bundle results =
          fhirClient
              .search()
              .forResource(clazz)
              .where(Patient.IDENTIFIER.exactly().identifier(ref))
              .returnBundle(Bundle.class)
              .execute();
      if (!results.getEntry().isEmpty()) {
        // Return the first match (assuming unique identifiers)
        return (DomainResource) results.getEntryFirstRep().getResource();
      }
    } catch (ResourceNotFoundException e) {
      // If the patient is not found, return false
      log.info("Patient with Ref:" + ref + " not found.");
    } catch (Exception e) {
      // Handle other possible exceptions (e.g., network issues, invalid server response)
      throw new RuntimeException("Error occurred while checking patient existence", e);
    }
    return null;
  }

  /**
   * Fetch Practitioner from PostOffice using any identifier.
   *
   * @param practitionerRef
   * @return
   */
  public Patient fetchPractitionerWithref(String practitionerRef) {
    Patient patient = null;
    try {
      Bundle results =
          fhirClient
              .search()
              .forResource(Patient.class)
              .where(Patient.IDENTIFIER.exactly().identifier(practitionerRef))
              .returnBundle(Bundle.class)
              .execute();
      if (results.getEntry().isEmpty()) {
        return null; // No patient found with this identifier
      } else {
        // Return the first match (assuming unique identifiers)
        patient = (Patient) results.getEntryFirstRep().getResource();
      }

    } catch (ResourceNotFoundException e) {
      // If the patient is not found, return false
      log.info("Practitioner with Ref:" + practitionerRef + " not found.");
    } catch (Exception e) {
      // Handle other possible exceptions (e.g., network issues, invalid server response)
      throw new RuntimeException("Error occurred while checking Practitioner existence", e);
    }
    return patient;
  }

  /**
   * Extracts the Organization resource associated with the given reference from the Bundle.
   *
   * @param bundle the Bundle object
   * @param reference the organization reference (Organization ID)
   * @return the Organization object if found, otherwise null
   */
  public Organization getBeneficiary(Bundle bundle, String reference) {
    Organization organization = null;
    // Loop through all entries in the bundle to find the corresponding Organization resource
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Organization) {
        organization = (Organization) entry.getResource();
        // Check if the Organization's ID matches the given reference
        if (organization.getIdElement().getValue().equals(reference)) {
          break;
        }
      }
    }
    return organization;
  }

  /**
   * Method to search ClaimResponse using a Claim reference.
   *
   * @param claimReference The reference to the Claim (e.g., "Claim/1234").
   * @return List of matching ClaimResponse resources.
   */
  public List<ClaimResponse> searchClaimResponseByClaimReference(String claimReference) {
    List<ClaimResponse> claimResponseList = new ArrayList<>();

    try {
      // Perform the search query for ClaimResponse by Claim reference
      Bundle results =
          fhirClient
              .search()
              .forResource(ClaimResponse.class)
              .where(ClaimResponse.REQUEST.hasId(claimReference)) // Search by Claim reference
              .returnBundle(Bundle.class)
              .execute();

      // Iterate over the entries and add them to the result list
      results
          .getEntry()
          .forEach(
              entry -> {
                claimResponseList.add((ClaimResponse) entry.getResource());
              });

    } catch (Exception e) {
      // Handle any errors
      throw new RuntimeException(
          "Error occurred while searching for ClaimResponse by Claim reference", e);
    }

    return claimResponseList;
  }

  /**
   * Method to search Bundled ClaimResponses.
   *
   * @param claimReference The reference to the Claim (e.g., "Claim/1234").
   * @return List of matching ClaimResponse resources.
   */
  public List<Bundle> searchClaimBundles(String claimReference) {
    List<Bundle> claimResponseList = new ArrayList<>();
    try {
      // Perform the search query for ClaimResponse by Claim reference
      Bundle results =
          fhirClient.search().forResource(Bundle.class).returnBundle(Bundle.class).execute();

      // Iterate over the entries and add them to the result list
      results
          .getEntry()
          .forEach(
              entry -> {
                claimResponseList.add((Bundle) entry.getResource());
              });

    } catch (Exception e) {
      throw new RuntimeException(
          "Error occurred while searching for ClaimResponse by Claim reference", e);
    }
    return claimResponseList;
  }

  /**
   * Creates a ClaimResponse object from the provided Claim.
   *
   * @param claim the Claim object
   * @param patient
   * @return the corresponding ClaimResponse
   */
  @Async("virtualThreadExecutor")
  public void startShaBusinessProcess(Claim claim, ClaimResponse claimResponse, Member patient) {
    //String gender = patient.getGender();
    //int age = FhirHelper.calculateAge(patient.getBirthDate());
    Map<String, Object> workflowParams = new HashMap<String, Object>();
    /*int lengthOfStay = FhirHelper.calculateLoS(claim);

    // TODO: This is assumption that system has validated the PreAuth for correctness before here.
    boolean hasPreAuth = false;
    if(claim.hasInsurance() && claim.getInsurance().getFirst().hasPreAuthRef()){
      hasPreAuth = true;
    }
    if(claim.hasRelated()){
      List<Claim.RelatedClaimComponent> relatedClaims = claim.getRelated();
      for (Claim.RelatedClaimComponent relatedClaim : relatedClaims) {
        String relationshipCode = "";
        CodeableConcept relationship = relatedClaim.getRelationship();
        if (relationship != null && !relationship.getCoding().isEmpty()) {
          Coding coding = relationship.getCodingFirstRep();
          relationshipCode = coding.getCode();
          if("pre-auth".equalsIgnoreCase(relationshipCode)){
            hasPreAuth = true;
          }
        }
      }
    }

    String serviceCatagory = "inpatient"; // Defaults to inpatient as it can be any.
    if(claim.hasSubType()){
      serviceCatagory = claim.getSubType().getText();
      String sybType = claim.getSubType().getCoding().getFirst().getCode();
      switch (sybType) {
        case "op":
          serviceCatagory = "outpatient";
          break;
        case "ip":
          serviceCatagory = "inpatient";
          break;
        case "emr":
          serviceCatagory = "emergency";
          break;
        default:
          serviceCatagory = "inpatient";
          break;
      }
    }

    // Prepare Claim level Inputs for workflow. Item level inputs will be added.
    List<Map<String,Object>> wfIntereventionInputs = new ArrayList<>();
    // Add adjudication and other details (this is simplified)
    for (Claim.ItemComponent item : claim.getItem()) {
      Map<String, Object> adjudicationDecisionInputs = new HashMap<String, Object>();
      Coding intervention = item.getProductOrService().getCoding().get(0);
      String interventionCode = intervention.getCode();

      adjudicationDecisionInputs.put("gender", gender);
      adjudicationDecisionInputs.put("age", age);
      adjudicationDecisionInputs.put("hasPreAuth", hasPreAuth);
      adjudicationDecisionInputs.put("individualUsage", 0);
      adjudicationDecisionInputs.put("serviceCategory", serviceCatagory);
      adjudicationDecisionInputs.put("code", interventionCode);
      adjudicationDecisionInputs.put("sequence", item.getSequence());
      adjudicationDecisionInputs.put("claimAmount", item.getNet().getValue().doubleValue());
      adjudicationDecisionInputs.put("los", lengthOfStay);

      //TODO: Get this data fixed.
      //adjudicationDecisionInputs.put("facilityCategory", facilityCategory.toLowerCase());
      adjudicationDecisionInputs.put("interventionUsageCount", 1);
      adjudicationDecisionInputs.put("householdUsage", 1);

      wfIntereventionInputs.add(adjudicationDecisionInputs);
    }*/
    workflowParams.put(VAR_CLAIM_ID, claim.getIdPart());
    workflowParams.put(VAR_CLAIM_RESPONSE_ID, claimResponse.getIdPart());
    workflowParams.put(VAR_CLAIM_TYPE, claim.getUse());
    //workflowParams.put(VAR_STATE, "queued");
    //workflowParams.put(VAR_INTERVENTIONS, wfIntereventionInputs);
    wfService.startBusinessProcess(claim.getUse(),workflowParams);
  }

  public String findProviderLevel(Bundle bundle, Claim claim) {
    String providerLevel = "Level 3";
    Reference providerRef = claim.getProvider();
    Reference facilityRef = claim.getFacility();
    Organization provider = null;
    Location facilityLocation = null;
    // Loop through all entries in the bundle to find the provider resource
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (providerRef != null && providerRef.equals(entry.getFullUrl())) {
        // Return the Practitioner or Organization resource
        Resource resource = entry.getResource();
        if (resource instanceof Practitioner || resource instanceof Organization) {
          provider = (Organization) resource;
          // TODO: Find Provider level.
        }
      }else if (facilityRef != null && facilityRef.equals(entry.getFullUrl())) {
        Resource resource = entry.getResource();
        if (resource instanceof Practitioner || resource instanceof Organization) {
          facilityLocation = (Location) resource;
          //TODO: Find Provider level.
        }
      }
    }
    if(facilityLocation != null){

    }else if(provider != null){
      //facilityRepository.findByMflCode(provider.getIdentifier());
    }
    return providerLevel;
  }

  /**
   * Extracts the beneficiary information from the Coverage resource within a Bundle.
   *
   * @param bundle the Bundle object
   * @return the beneficiary (Patient reference) if found, otherwise null
   */
  public String getBeneficiaryFromCoverage(Bundle bundle) {
    String beneficiary = null;
    // Loop through all entries in the bundle to find Coverage resources
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Coverage) {
        Coverage coverage = (Coverage) entry.getResource();
        // Get the beneficiary reference from the Coverage resource
        Reference beneficiaryReference = coverage.getBeneficiary();
        if (beneficiaryReference != null) {
          // Return the beneficiary's reference (typically a Patient ID)
          beneficiary = beneficiaryReference.getReference();
        }
      }
    }
    return beneficiary;
  }

  /**
   * Extracts the Patient resource associated with the beneficiary reference from the Bundle.
   *
   * @param bundle the Bundle object
   * @param beneficiaryReference the beneficiary reference (Patient ID)
   * @return the Patient object if found, otherwise null
   */
  public Patient getBeneficiaryPatient(Bundle bundle, String beneficiaryReference) {
    Patient patient = null;
    // Loop through all entries in the bundle to find the corresponding Patient resource
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      if (entry.getResource() instanceof Patient) {
        patient = (Patient) entry.getResource();
        // Check if the Patient's ID matches the beneficiary reference
        if (patient.getIdElement().getValue().equals(beneficiaryReference)) {
          break;
        }
      }
    }
    return patient;
  }

  // Method to find all claims by a specific provider
  public List<String> findClaimsByProvider(String providerId, String type, String id) {
    // Perform a search on the Claim resource where the provider reference matches the providerId
    List<String> claims = new ArrayList<>();
    var search = fhirClient.search()
            .forResource(Claim.class)
            .where(Claim.PROVIDER.hasId(providerId));
    // Apply filter for the Use field if provided
    if (type != null && !type.isEmpty()) {
        search.where(Claim.USE.exactly().code(type));
    }
    if (id != null && !id.isEmpty()) {
      search.where(Claim.IDENTIFIER.exactly().identifier(id));
    }
    Bundle results = search.returnBundle(Bundle.class).execute();

    // Extract Claim resources from the Bundle
    for (Bundle.BundleEntryComponent entry : results.getEntry()) {
      Resource resource = entry.getResource();
      if (resource instanceof Claim) {
        claims.add(fhirParser.encodeToString(resource));
      }
    }
    return claims;
  }

  // Method to find claimResponse by Id
  public ClaimResponse findClaimResponseById(String id) {
    ClaimResponse claimResponse = null;
    try {
      claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(id).execute();
    }catch (ResourceNotFoundException e){
      log.info("ClaimResponse Resource with ID: {} not found", id);
    }
    return claimResponse;
  }

  /**
   * Method find claim by Id
   */
  public Claim findClaimById(String id) {
    Claim claim = null;
    try {
      claim = fhirClient.read().resource(Claim.class).withId(id).execute();
    }catch (ResourceNotFoundException e){
      log.info("Claim Resource with ID: {} not found", id);
    }
    return claim;
  }

  // Method to process the Bundle and update it with references
  public Bundle processAndStoreBundle(Bundle bundle) {
    // Iterate over each entry in the Bundle
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      Resource resource = entry.getResource();

      // Store the resource on the FHIR server and get the outcome
      MethodOutcome outcome = fhirClient.create()
              .resource(resource)
              .execute();

      // Get the ID of the newly created resource
      String resourceId = outcome.getId().getIdPart();
      String resourceType = resource.getResourceType().name();

      // Update the fullUrl to point to the newly created resource
      entry.setFullUrl(resourceType + "/" + resourceId);

      // Optionally, set the entry's reference as well
      if (entry.getRequest() != null && entry.getRequest().getUrl() != null) {
        entry.getRequest().setUrl(resourceType + "/" + resourceId);
      }
    }

    // Store the updated Bundle itself
    fhirClient.create()
            .resource(bundle)
            .execute();

    return bundle;
  }

  // Method to find a resource in a bundle by its URL
  public Resource getResourceByUrl(Bundle bundle, String url, String id) {
    Resource resource = null;
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      //log.info("Checking bundle with url :{} and id: {}. Current ResourceType: {} with id {}",
      //        url,id, entry.getResource().getResourceType().toString(), entry.getResource().getIdPart());
      // Check if the entry has a matching fullUrl
      if (entry.hasFullUrl() && entry.getFullUrl().contains(url)) {
        resource = entry.getResource();  // Return the resource if URL matches
      } else if (entry.getResource().getId() != null && (entry.getResource().getId().equalsIgnoreCase(id)
              || entry.getResource().getId().equalsIgnoreCase(url) || entry.getResource().getIdPart().equalsIgnoreCase(id))) {
        resource = entry.getResource();
      }
    }
    if (resource == null) {
      log.info("Resource with id {} and url {} not found.", id, url);
    }
    return resource;  // Return null if no matching resource is found
  }

  public String getPatientIdentifierValueByType(Patient patient, String identifierTypeCode) {
    // Iterate through the identifiers in the patient object
    for (Identifier identifier : patient.getIdentifier()) {
      // Check if the identifier has a type and matches the provided type code
      if (identifier.getType() != null && identifier.getType().getCoding().size() > 0) {
        String typeCode = identifier.getType().getCoding().get(0).getCode();
        if (typeCode.equals(identifierTypeCode)) {
          // Return the identifier value if found
          return identifier.getValue();
        }
      }
    }
    return null;
  }

  public String getOrgIdentifierValueByType(Organization org, String identifierTypeCode) {
    // Iterate through the identifiers in the patient object
    for (Identifier identifier : org.getIdentifier()) {
      // Check if the identifier has a type and matches the provided type code
      if (identifier.getType() != null && identifier.getType().getCoding().size() > 0) {
        String typeCode = identifier.getType().getCoding().get(0).getCode();
        if (typeCode.equals(identifierTypeCode)) {
          // Return the identifier value if found
          return identifier.getValue();
        }
      }
    }
    return null;
  }

  /**
   * Get Claim Response by ClaimId Reference.
   * @param claimId
   * @return
   */
  public ClaimResponse getClaimResponseByClaimId(String claimId, String claimResponseId) {
    if (claimResponseId != null) {
      ClaimResponse response = getResourceById(ClaimResponse.class, claimResponseId);
      if (response != null) {
        return response;
      }
    }
    // Search for ClaimResponse by claim reference
    ClaimResponse claimResponse = null;
    Bundle bundle = fhirClient.search()
            .forResource(ClaimResponse.class)
            .where(ClaimResponse.REQUEST.hasId(claimId)).count(50) // Searching by claim ID
            .returnBundle(Bundle.class)
            .execute();

    //Ensure that we are not getting null pointer if result is empty.
    if (bundle.hasEntry() && !bundle.getEntry().isEmpty()) {
      // Note : There has been cases where we unknowingly created multiple responses against single id.
      if (bundle.getEntry().size() > 1) {
        for (Bundle.BundleEntryComponent obj : bundle.getEntry()) {
          claimResponse = (ClaimResponse) obj.getResource();
          if (claimResponse.hasItem()) {
            break;
          }
        }
      } else {
        claimResponse = (ClaimResponse) bundle.getEntry().getFirst().getResource();
      }
    }
    if (claimResponse != null && claimResponse.hasTotal()) {
      ClaimResponse.TotalComponent total = claimResponse.getTotal().getFirst();
      if (total.hasAmount() && total.getAmount().getCurrency().equalsIgnoreCase("KSH")) {
        claimResponse.getTotal().getFirst().getAmount().setCurrency(CURRENCY_SHILLING);
        fhirClient.update().resource(claimResponse).execute();
      }
    }
    return claimResponse;
  }

  /**
   * Get all ClaimResponses for a claimId
   * @param claimId
   * @return bundle with claimResponses for given claimId
   */
  public Bundle getAllClaimResponsesByClaimId(String claimId) {
    // Search for ClaimResponse by claim reference
    Bundle bundle = fhirClient.search()
            .forResource(ClaimResponse.class)
            .where(ClaimResponse.REQUEST.hasId(claimId)).count(50) // Searching by claim ID
            .returnBundle(Bundle.class)
            .execute();
    return bundle;
  }

  /**
   * find bundle which has a claim resource with given id
   * @param claimId
   * @return Bundle
   */
  public Bundle findBundleByClaimId(String claimId) {
    Bundle bundle = null;
    try {
      bundle = fhirClient.search()
              .forResource(Claim.class)
              .where(Claim.IDENTIFIER.exactly().identifier(claimId))
              .returnBundle(Bundle.class)
              .execute();
    } catch (ResourceNotFoundException e) {
      log.error("Bundle Resource with ClaimID: {} not found", claimId);
      throw new RuntimeException(e);
    }
    return bundle;
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
              .withId(resourceId)
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


  public Task getTaskByClaimId(String claimId) {
    try {
      // Search using focus.identifier without specifying the system
      Bundle bundle = fhirClient.search()
              .forResource(Task.class)
              .where(new ReferenceClientParam("focus")
                      .hasChainedProperty(new TokenClientParam("identifier")
                              .exactly()
                              .code(claimId))) // Only search by claimId
              .returnBundle(Bundle.class)
              .execute();

      // Return the first Task if available
      if (!bundle.getEntry().isEmpty()) {
        return (Task) bundle.getEntry().get(0).getResource();
      } else {
        return null; // No matching Task found
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to fetch Task by claim ID", e);
    }
  }

}
