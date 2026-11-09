package io.coredge.health.claims.rest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import io.coredge.health.claims.service.HealthExchangeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CoverageEligibilityRequest;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.coredge.health.claims.constants.Constants.EndPoints.HIE_REGISTRY_PATH;

@RestController
@RequestMapping(HIE_REGISTRY_PATH)
@Slf4j
public class HIEController {

    @Autowired
    private FhirContext fhirContext;

    @Autowired
    private HealthExchangeService hieService;

    @Autowired
    private IParser fhirParser;

    // get endpoint to fetch a Patient object from HIE
    @GetMapping(path = "/patient", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchPatient(@RequestParam String searchType, @RequestParam String searchKey) {
        try {
            Patient patient = hieService.fetchFhirPatient(searchType, searchKey, false);
            String result = fhirParser.encodeResourceToString(patient);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(path = "/facility", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchFacility(@RequestParam String searchType, @RequestParam String searchKey) {
        try {
            Organization org = hieService.fetchFhirFacility(searchType, searchKey);
            String result = fhirParser.encodeResourceToString(org);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(path = "/sync/facilities", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> syncFacilities() {
        try {
            hieService.syncFacilities();
            return new ResponseEntity<>("{}", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/upload/json")
    public ResponseEntity<String> uploadCsvFile(@RequestBody List<Map<String,String>> body) {
        if (body.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        hieService.syncFacility(body);
        return ResponseEntity.ok("Started syncing");
    }

    @PostMapping("/upload/csv")
    public ResponseEntity<String> uploadCsvFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        hieService.syncFacility(file);
        return ResponseEntity.ok("Started syncing");
    }

    @GetMapping(path = "/sync/facilities/batches", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> syncFacilitiesBatches() {
        try {
            hieService.syncDbFacility();
            return new ResponseEntity<>("{}", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(path = "/practitioner", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchPractitioner(@RequestParam String searchType, @RequestParam String searchKey) {
        try {
            Practitioner practitioner = hieService.fetchFhirPractitioner(searchType, searchKey);
            String result = fhirParser.encodeResourceToString(practitioner);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(path = "/eligibility", produces = MediaType.APPLICATION_JSON_VALUE)
    public String checkEligibility(@RequestBody String eligibilityJson) {
        IParser parser = fhirContext.newJsonParser();
        String result = "";
        try {
            CoverageEligibilityRequest request = parser.parseResource(CoverageEligibilityRequest.class, eligibilityJson);
            result = hieService.checkEligibility(request.getPatient().getReference());
        } catch (Exception ex) {
            return ex.toString();
        }
        return result;
    }
}
