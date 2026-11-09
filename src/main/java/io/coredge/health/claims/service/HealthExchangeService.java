package io.coredge.health.claims.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.coredge.health.claims.client.HieClient;
import io.coredge.health.claims.client.SafaricomHieClient;
import io.coredge.health.claims.db.entity.HieProvider;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.repositories.HieProviderRepository;
import io.coredge.health.claims.db.repositories.ProviderRepository;
import io.coredge.health.claims.enums.HieSource;
import io.coredge.health.claims.enums.IdentifierType;
import io.coredge.health.claims.exception.app.HIEParseException;
import io.coredge.health.claims.exception.app.ProviderNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class HealthExchangeService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private final RestTemplate restTemplate;

    @Value("${HIE_MEMBER_URI}")
    String HIE_MEMBER_URI;

    @Value("${HIE_FACILITY_URI}")
    String HIE_FACILITY_URI;

    @Value("${SAF_HIE_FACILITY_URI}")
    String SAF_HIE_FACILITY_URI;

    @Value("${HIE_WORKER_URI}")
    String HIE_WORKER_URI;

    @Value("${ELIGIBILITY_URI:https://api.dha.go.ke/v2/}")
    String ELIGIBILITY_URI;

    @Value("${HIE_USERNAME}")
    String HIE_USERNAME;

    @Value("${HIE_AUTH_URI}")
    String HIE_AUTH_URI;

    @Value("${HIE_PASSWORD}")
    String HIE_PASSWORD;

    @Value("${HIE_KEY:apeiro_apps}")
    String HIE_KEY;

    @Value("${HIE_REFER:https://eclaims.apeiro-digital.com}")
    String HIE_REFERRER;

    @Value("${hie.provider.source:SAFARICOM}")
    String HIE_SOURCE;


    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private FhirContext fhirContext;
    @Autowired
    private IParser fhirParser;
    @Autowired
    private ProviderRepository providerRepository;
    @Autowired
    private HieProviderRepository hieProviderRepository;

    @Autowired
    private SafaricomHieClient safaricomHieClient;

    @Autowired
    private HieClient hieClient;
    @Autowired
    private MemberService memberService;

    private static final int THREAD_POOL_SIZE = 10;

    public HealthExchangeService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String generateJwtToken() {
        // Define the URL and parameters
        String url = HIE_AUTH_URI;
        UriComponentsBuilder uriBuilder =
                UriComponentsBuilder.fromHttpUrl(url).queryParam("key", HIE_KEY);

        // Create Basic Authentication header
        String auth = HIE_USERNAME + ":" + HIE_PASSWORD;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + encodedAuth;

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Referer", HIE_REFERRER);
        headers.set("x-source-msisdn", HIE_KEY);

        // Create the request entity
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Send the GET request
        ResponseEntity<String> response =
                restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.GET, entity, String.class);

        // Return the JWT token from the response body
        return response.getBody(); // Modify this as per response format to extract JWT
    }


    /**
     * Fetch Patient data from HIE with caching.
     * Possible to search using "national-id", "passport", and "birth-certificate-number"
     *
     * @param searchType Type of identifier (e.g., "national-id")
     * @param searchKey  Value of identifier (e.g., actual ID number)
     * @return FHIR-compliant patient data.
     */
    @Cacheable(value = "patients", key = "#searchType + '-' + #searchKey")
    public String fetchPatient(String searchType, String searchKey) {
        // Define the URL and parameters
        String url = HIE_MEMBER_URI;
        UriComponentsBuilder uriBuilder =
                UriComponentsBuilder.fromHttpUrl(url)
                        .queryParam("identifierType", URLEncoder.encode(searchType, StandardCharsets.UTF_8))
                        .queryParam("identifierNumber", searchKey);
        return hieClient.fetchDetails(uriBuilder);
    }

    /**
     * Fetch Practitioner data from HIE.
     *
     * @param searchType possible values are "national-id", "id" and "board-registration-number",
     *                   "license-number", client-registry-id"
     * @param searchKey  Value
     * @return FHIR compliant practitioner data as bundle.
     */
    @Cacheable(value = "practitioners", key = "#searchType + '-' + #searchKey")
    public String fetchPractitioner(String searchType, String searchKey) {
        // Define the URL and parameters
        String url = HIE_WORKER_URI;
        UriComponentsBuilder uriBuilder =
                UriComponentsBuilder.fromHttpUrl(url)
                        .queryParam("identifierType", URLEncoder.encode(searchType, StandardCharsets.UTF_8))
                        .queryParam("identifierNumber", searchKey);

        String practitionerJson = hieClient.fetchDetails(uriBuilder);
        try {
            JsonNode practitionerNode = objectMapper.readTree(practitionerJson).path("entry").path(0).path("resource");
            return objectMapper.writeValueAsString(practitionerNode);
        } catch (JsonProcessingException e) {
            log.error("HIE Practitioner Json: {}", practitionerJson);
            throw new RuntimeException(e);
        }
    }

    /**
     * Fetch Facility data from HIE. Possible to search using "national-id", "passport" and
     * "birth-certificate-number"
     *
     * @param searchType possible values are "facility-code [fr-code]", "facility-registration-number[registration-number]",
     *                   "mfl code [mfl-code]", "id"
     * @return FHIR compliant practitioner data.
     */
    public String fetchFacility(String searchType, String searchValue) {
        if (HIE_SOURCE.equalsIgnoreCase(HieSource.DHA.name())) {
            return fetchProviderFromDHA(searchValue, IdentifierType.getIdentifier(searchType));
        } else {
            return fetchProviderFromSafariCom(searchValue, IdentifierType.getIdentifier(searchType));
        }
    }


    public Provider findProviderByMflCode(String mflCode) {
        Provider provider = null;
        if (mflCode.contains("-"))
            mflCode = mflCode.split("-")[1];

        String providerJson = fetchFacility("facility-code", mflCode);
        if (StringUtils.hasText(providerJson)) {
            Bundle bundle = fhirParser.parseResource(Bundle.class, providerJson);
            provider = Provider.fromHieFacility(bundle, null);
        }
        return provider;
    }

    /**
     * Fetch Patient Eligibility from HIE.
     *
     * @param searchKey
     * @return FHIR compliant patient data.
     */
    public String checkEligibility(String searchKey) {
        // Define the URL and parameters
        String url = ELIGIBILITY_URI;
        String result = "";
        if (searchKey.contains("/")) {
            searchKey = searchKey.split("/")[1];
            // patient = fetchPatientWithref(patientId);
        }
        UriComponentsBuilder uriBuilder =
                UriComponentsBuilder.fromHttpUrl(url)
                        .queryParam("identification_type", "National ID")
                        .queryParam("identification_number", searchKey);

        // Set headers
        String authToken = generateJwtToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authToken);
        headers.set("Referer", HIE_REFERRER);
        headers.set("x-source-msisdn", HIE_KEY);

        // Create the request entity
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            // Send the GET request
            ResponseEntity<String> response =
                    restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.GET, entity, String.class);
            result = response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            // If a 404 error occurs, the patient does not exist
        } catch (HttpClientErrorException he) {
            log.error(he.getMessage());
        }
        return result;
    }

    /**
     * Fetch Facility data from HIE.
     *
     * @param searchType possible values are "facility-code", "facility-registration-number",
     *                   "license-number", "id"
     * @param searchKey
     * @return FHIR Organization.
     */
    public Organization fetchFhirFacility(String searchType, String searchKey) {
        Organization org = null;
        try {
            String facilityJson = fetchFacility(searchType, searchKey);
            Bundle bundle = fhirParser.parseResource(Bundle.class, facilityJson);
            if (bundle.getEntry().isEmpty()) {
                log.error("No data found : {}", searchKey);
                throw new RuntimeException("No data found");
            }
            org = (Organization) bundle.getEntry().getFirst().getResource();
        } catch (Exception ex) {
            log.error(ex.toString());
        }
        return org;
    }


    /**
     * Fetch Provider data from HIE. Possible to search using "FrCode" and "identifier".
     *
     * @param identifierType The Master Facility List (FrCode) code for identifying the provider.
     * @param searchValue    Additional identifier to narrow down the search.
     * @return FHIR-compliant provider data.
     */
    @Cacheable(value = "providers", key = "#searchValue + '-' + #identifierType")
    public String fetchProviderFromDHA(String searchValue, IdentifierType identifierType) {
        // Define the URL and parameters
        String url = HIE_FACILITY_URI;
        log.info("Identifier type , {}", identifierType.getIdentifierType());
        UriComponentsBuilder uriBuilder =
                UriComponentsBuilder.fromHttpUrl(url)
                        .queryParam("identifierType", URLEncoder.encode(identifierType.getIdentifierType(), StandardCharsets.UTF_8))
                        .queryParam("identifier", URLEncoder.encode(searchValue, StandardCharsets.UTF_8));

        String providerJson = hieClient.fetchDetails(uriBuilder);

        try {
            int totalLink = Integer.parseInt(objectMapper.readTree(providerJson).get("total").asText());
            if (totalLink > 0)
                return objectMapper.writeValueAsString(objectMapper.readTree(providerJson).path("entry").path(0).path("resource"));
            else {
                log.warn("Provider Data Not Found in HIE for: {}", searchValue);
                throw new RuntimeException(String.format("Provider Data Not Found in HIE for: %s", searchValue));
            }
        } catch (JsonProcessingException e) {
            log.error("HIE Provider Json: {}", providerJson);
            throw new RuntimeException(e);
        }
    }


    /**
     * Fetch Provider data from HIE. Possible to search using "FrCode" and "identifier".
     *
     * @param identifierType The Master Facility List (FrCode) code for identifying the provider.
     * @param searchValue    Additional identifier to narrow down the search.
     * @return FHIR-compliant provider data.
     */
    @Cacheable(value = "providers", key = "#searchValue + '-' + #identifierType")
    public String fetchProviderFromSafariCom(String searchValue, IdentifierType identifierType) {
        String url = SAF_HIE_FACILITY_URI;

        log.info("🔎 Searching provider in SAFARICOM HIE with Identifier Type: {}, identifier: {}", identifierType.getIdentifierType(), searchValue);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("identifier-type", URLEncoder.encode(identifierType.getIdentifierType(), StandardCharsets.UTF_8))
                .queryParam("identifier", URLEncoder.encode(searchValue, StandardCharsets.UTF_8));

        String providerJson = safaricomHieClient.fetchDetails(uriBuilder);

        if (!StringUtils.hasText(providerJson)) {
            throw new ProviderNotFoundException(searchValue);
        }

        try {
            JsonNode rootNode = objectMapper.readTree(providerJson);
            int total = rootNode.path("total").asInt(0);

            if (total > 0) {
                return objectMapper.writeValueAsString(rootNode.path("entry").path(0).path("resource"));
            } else {
                throw new ProviderNotFoundException(searchValue);
            }

        } catch (JsonProcessingException e) {
            log.error("🚨 JSON parsing failed for HIE response: {}", providerJson, e);
            throw new HIEParseException(searchValue, providerJson, e);
        }
    }

    /**
     * Fetch and Sync Facility data from HIE and update database table.
     *
     * @return void.
     */
    @Async("virtualThreadExecutor")
    public void syncFacilities() throws InterruptedException {
        List<String> missingProvidersList = new ArrayList<String>();
        for (Provider provider : providerRepository.findAll().reversed()) {
            Thread.sleep(200);
            try {
                String facilityJson = null;
                //Skip records which are already synced to save time in case re-run.
                if (hieProviderRepository.existsByMflCode(provider.getMflCode()).get()) {
                    log.error("Facility with MFL Code: {} is already found in Internal registry.", provider.getMflCode());
                    continue;
                }
                if (provider.getIdentifier().startsWith("FID")) {
                    facilityJson = fetchFacility("fr-code", provider.getIdentifier());
                } else {
                    facilityJson = fetchFacility("mfl-code", provider.getMflCode());
                }
                Bundle bundle = fhirParser.parseResource(Bundle.class, facilityJson);
                if (bundle.hasEntry()) {
                    HieProvider hieProvider = HieProvider.fromHieFacility(bundle);
                    hieProviderRepository.save(hieProvider);
                    Organization fhirOrg = (Organization) bundle.getEntry().getFirst().getResource();
                    fhirClient.update().resource(fhirOrg).execute();
                    log.info("Updated Provider with ID:{} and MFL Code:{} from HIE registry", provider.getId(), provider.getMflCode());
                } else {
                    missingProvidersList.add(provider.getMflCode());
                    log.error("Facility with MFL Code: {} is not found in HIE registry", provider.getMflCode());
                }
            } catch (Exception ex) {
                log.error(ex.toString());
            }
        }
        log.error("MISSING MFL CODES IN HIE {}", missingProvidersList);
    }

    /**
     * Fetch Practitioner data from HIE.
     *
     * @param searchType possible values are "national-id", "id" and "board-registration-number",
     *                   "license-number", client-registry-id"
     * @param searchKey
     * @return FHIR compliant Practitioner.
     */
    public Practitioner fetchFhirPractitioner(String searchType, String searchKey) {
        Practitioner practitioner = null;
        try {
            String facilityJson = fetchPractitioner(searchType, searchKey);
            IParser parser = fhirContext.newJsonParser();
            parser.setParserErrorHandler(new StrictErrorHandler());
            practitioner = parser.parseResource(Practitioner.class, facilityJson);
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error(ex.toString());
        }
        return practitioner;
    }

    /**
     * Fetch Patient data from HIE. Possible to search using "national-id", "passport" and
     * "birth-certificate-number"
     *
     * @param searchType
     * @param searchKey
     * @return FHIR Patient.
     */
    public Patient fetchFhirPatient(String searchType, String searchKey, Boolean isPushDB) {
        Patient patient = new Patient();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String patientJson = fetchPatient(searchType, searchKey);
            log.info("HIE patient Json: {}", patientJson);
            if (isPushDB) {
                // Save or Update Member in DB
                memberService.saveToDatabase(Member.fromJsonBundle(patientJson));
            }
            Bundle bundle = fhirParser.parseResource(Bundle.class, patientJson);
            if (bundle.getEntry().isEmpty()) {
                log.warn("No data found : {}", searchKey);
                throw new RuntimeException("No data found");
            }
            patient = (Patient) bundle.getEntry().getFirst().getResource();

//      // Remove specific element as it not showing as HFIR compliant.
//      // TODO: remove it once HIE is stable.
//      JsonNode rootNode = objectMapper.readTree(patientJson);
//      if (rootNode.has("address")) {
//        ArrayNode addressNode = (ArrayNode) rootNode.get("address");
//        Iterator<JsonNode> elements = addressNode.elements();
//        while (elements.hasNext()) {
//          JsonNode element = elements.next();
//          if (element.has("extension")) {
//            ((ObjectNode) element).remove("extension");
//          }
//        }
//      }
////      rootNode = objectMapper.readTree(patientJson).path("entry").path(0).path("resource");
////      patientJson = objectMapper.writeValueAsString(rootNode);
//      IParser parser = fhirContext.newJsonParser();
//      patient = parser.parseResource(Patient.class, patientJson);

        } catch (Exception ex) {
            ex.printStackTrace();
            log.error(ex.toString());
        }
        return patient;
    }

    /**
     * Syncing HIE data asynchronously in parallel.
     *
     * @param body List of json containing the data to be synced.
     */
    public void syncFacility(List<Map<String, String>> body) {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<String> missingProvidersList = new ArrayList<>();
        try {
            // Create a list of CompletableFutures to execute sync tasks in parallel
            List<CompletableFuture<Void>> futures = body.stream()
                    .map(map -> CompletableFuture.runAsync(() -> startSync(map, missingProvidersList), executorService))
                    .toList();

            // Combine all futures and wait for their completion
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allFutures.join(); // Wait for all tasks to complete
        } finally {
            for (String provider : missingProvidersList) {
                log.info("Missing Provider: {}", provider);
            }
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void startSync(Map<String, String> body, List<String> missingProvidersList) {
        log.info("Started sync from HIE");

        String codeType = body.containsKey("mflCode") ? "mfl-code" : "fr-code";
        String code = body.getOrDefault("mflCode", body.get("frCode"));

        if (code == null) {
            log.warn("No valid facility code provided in the request body.");
            return;
        }

        try {
//      if (facilityExists(codeType, code)) {
//        log.info("Facility with {} Code: {} found in the internal registry. Updating facility.", codeType, code);
//        Optional<Provider> existingProvider = findFacility(codeType, code);
//        updateFacility(codeType, code, existingProvider.get(), missingProvidersList);
//      } else {
            log.info("Facility with {} Code: {} not found in the internal registry. Fetching and saving.", codeType, code);
            updateFacility(codeType, code, null, missingProvidersList);
//      }
        } catch (Exception ex) {
            log.error("Error processing facility with {} Code: {}: {}", codeType, code, ex.getMessage());
        }
    }

//  private boolean facilityExists(String codeType, String code) {
//    return codeType.equals("mfl-code") ? providerRepository.existsByMflCode(code).orElse(false) : providerRepository.existsByFid(code).orElse(false);
//  }
//
//  private Optional<Provider> findFacility(String codeType, String code) {
//    return codeType.equals("mfl-code") ? providerRepository.findByMflCode(code) : providerRepository.findByFid(code);
//  }

    public void syncFacility(MultipartFile file) {
        log.info("Started sync from HIE");
        List<String> missingProvidersList = new ArrayList<>();
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isFirstRow = true;

            while ((line = fileReader.readLine()) != null) {
                // Skip the header row
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }

                // Split the line into columns (assuming comma as the delimiter)
                String[] columns = line.split(",");

                // Ensure we have enough columns
                if (columns.length < 5) {
                    log.warn("Skipping malformed row: {}", line);
                    continue;
                }

                // Extract data from columns
                String mflCode = columns[3].trim();
                String frCode = columns[5].trim();
                String licenseNo = columns[4].trim();
                String level = columns[2].trim();
                String name = columns[1].trim();

                // Update using FID in FHIR
                try {
                    // Skip records already synced
                    if (providerRepository.existsByMflCode(frCode).get() || providerRepository.existsByIdentifier(mflCode).get()) {
                        log.error("Facility with MFL Code: {} is already found in Internal registry.", frCode);
                        continue;
                    }

                    String facilityJson = fetchFacility("fr-code", frCode);
                    Bundle bundle = fhirParser.parseResource(Bundle.class, facilityJson);

                    if (bundle.hasEntry()) {
                        Provider provider = Provider.fromHieFacility(bundle, null);
                        providerRepository.save(provider);
                        Organization fhirOrg = (Organization) bundle.getEntry().getFirst().getResource();
                        fhirOrg.setId("Organization/" + frCode);
                        fhirClient.update().resource(fhirOrg).execute();
                        log.info("Updated Provider with Id:{} and MFL Code:{} from HIE registry", provider.getId(), frCode);
                    } else {
                        missingProvidersList.add(frCode);
                        log.error("Facility with MFL Code: {} is not found in HIE registry", frCode);
                    }
                } catch (Exception ex) {
                    log.error("Error processing facility with MFL Code: {}: {}", mflCode, ex.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Error reading the CSV file: {}", e.getMessage());
        }
        log.info("Completed sync from HIE");
    }

    /**
     * Syncing HIE data with Db asynchronously in parallel.
     */
    public void syncDbFacility() {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            List<Provider> providers = providerRepository.findAll();
//      Collections.reverse(providers);
            // Create a list to track CompletableFutures
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Provider provider : providers) {
                // Submit each task to the executor service
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> syncFacilityDb(provider), executorService);
                futures.add(future);
                // Introduce a small delay between thread submissions
                try {
                    Thread.sleep(200); // 200ms delay between thread submissions
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Thread sleep interrupted", e);
                }
            }
            // Wait for all futures to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allFutures.join(); // Wait for all tasks to complete
        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void syncFacilityDb(Provider provider) {
        log.info("Started sync from HIE");
        String codeType = null;
        String code = null;
        if (provider.getMflCode() != null) {
            codeType = "mfl-code";
            code = provider.getMflCode().replace("MFL-", "");
        } else if (provider.getFidCode() != null) {
            codeType = "fr-code";
            code = provider.getFidCode();
        }
        try {
            if (code == null) {
                log.warn("No valid facility code found.");
                return;
            }
            updateFacility(codeType, code, provider, new ArrayList<>());

        } catch (Exception ex) {
            log.error("Error processing facility with {} Code: {}: {}", codeType, code, ex.getMessage());
        }
    }

    private void updateFacility(String codeType, String code, Provider provider, List<String> missingProvidersList) {
        String facilityJson = fetchFacility(codeType, code);
        Bundle bundle = fhirParser.parseResource(Bundle.class, facilityJson);
        if (bundle.hasEntry()) {
            saveAndUpdateDate(bundle, provider, code);
        } else {
            log.error("Facility with {} Code: {} not found in HIE registry.", codeType, code);
            missingProvidersList.add(code);
        }
    }

    @Transactional
    protected void saveAndUpdateDate(Bundle bundle, Provider provider, String code) {
        HieProvider hieProvider = HieProvider.fromHieFacility(bundle);
        HieProvider hieProvd = hieProviderRepository.save(hieProvider);
//    Organization fhirOrg = (Organization) bundle.getEntry().getFirst().getResource();
//    if (!code.contains("FID")) {
//      code = "MFL-" + code;
//    }
//    fhirOrg.setId("Organization/" + code);
//    fhirClient.update().resource(fhirOrg).execute();
        log.info("Facility with {} Code: {} updated in Db from HIE registry.", hieProvd.getId(), code);
    }

//  @Transactional
//  private void saveAndUpdateDate(Bundle bundle, Provider provider, String code) {
//    Provider providerObj = null;
//    if (provider != null && provider.getId() != null) {
//      log.info("Already found in DB ,Updating Info,  Provider ID: {}", provider.getId());
//      Provider providerUpdated = Provider.fromHieFacility(bundle, provider.getId());
//      if (providerUpdated.getMflCode() != null && providerRepository.existsByMflCode(providerUpdated.getMflCode()).get()) {
//        Optional<List<Provider>> response = providerRepository.findByMflCode(providerUpdated.getMflCode());
//        if (response.isPresent() && !response.get().isEmpty()) {
//          Provider provFound = response.get().getFirst();
//          providerUpdated.setId(provFound.getId());
//          providerRepository.delete(provider);
//        }
//      }
//      providerObj = providerRepository.save(providerUpdated);
//    } else {
//      Provider providerUpdated = Provider.fromHieFacility(bundle, null);
//      providerObj = providerRepository.save(providerUpdated);
//    }
//    Organization fhirOrg = (Organization) bundle.getEntry().getFirst().getResource();
//    fhirOrg.setId("Organization/" + code);
//    fhirClient.update().resource(fhirOrg).execute();
//    log.info("Facility with {} Code: {} updated in Db and Fhir from HIE registry.", providerObj.getId(), code);
//  }

}
