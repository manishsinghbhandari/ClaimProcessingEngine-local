package io.coredge.health.claims.rest.impl;

import ca.uhn.fhir.parser.IParser;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.rest.FhirController;
import io.coredge.health.claims.service.FhirService;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;


@RestController
@RequestMapping(Constants.EndPoints.FHIR_PATH)
@Slf4j
public class FhirControllerImpl implements FhirController {


    @Autowired
    private FhirService service;
    @Autowired private IParser fhirParser;

    @GetMapping(Constants.EndPoints.FHIR_API_CLAIM)
    @Override
    public ResponseEntity<String> getByClaimId(@RequestParam String claimId) {
        return ResponseEntity.ok(service.fetchByClaimId(claimId));
    }

    @GetMapping(Constants.EndPoints.FHIR_API_CLAIM_RESPONSE)
    @Override
    public ResponseEntity<String> getByClaimResponseId(@RequestParam String claimResponseId) {
        return ResponseEntity.ok(service.fetchByClaimResponseId(claimResponseId));
    }

    @GetMapping(Constants.EndPoints.FHIR_API_PATIENT)
    @Override
    public ResponseEntity<String> getByPatientId(@RequestParam String patientId) {
        return ResponseEntity.ok(service.fetchByPatientId(patientId));
    }

    @GetMapping(Constants.EndPoints.FHIR_API_ORGANIZATION)
    @Override
    public ResponseEntity<String> getByOrganizationId(@RequestParam String mflCode) {
        return ResponseEntity.ok(service.fetchByOrganizationId(mflCode));
    }

    @GetMapping(path = Constants.EndPoints.FHIR_API_CLAIM_RES_BY_CLAIM_ID)
    @Override
    public ResponseEntity<String> getClaimResponseByClaimId(@RequestParam String claimId) {
        String responseJson = "";
        ClaimResponse claimResponse = service.fetchClaimResponseByClaimId(claimId);
        if(claimResponse != null){
            responseJson = fhirParser.encodeResourceToString(claimResponse);
        }else{
            log.warn("!!ClaimResponseByClaimId ClaimId {} not found.", claimId);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(responseJson);
    }

    @PostMapping(Constants.EndPoints.FHIR_API_REPLAY_CALLBACK)
    @Override
    public ResponseEntity<String> claimCallBack(@RequestParam String claimId) {
        return ResponseEntity.ok(service.claimCallBack(claimId));
    }

    //Code System APIs
    @GetMapping(value = Constants.EndPoints.FHIR_API_CODE_SYSTEM , produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public ResponseEntity<String> getAllCodeSystems() {
        return ResponseEntity.ok(service.getAllCodeSystems());
    }

    @GetMapping(value = Constants.EndPoints.FHIR_API_CODE_SYSTEM_BY_ID  , produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public ResponseEntity<String> getCodeSystemById(@PathVariable String id) {
        return ResponseEntity.ok(service.getCodeSystemById(id));
    }

    @PutMapping(value = Constants.EndPoints.FHIR_API_CODE_SYSTEM_BY_ID , consumes = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public ResponseEntity<String> createOrUpdateCodeSystem(@PathVariable String id, @RequestBody String codeSystemJson) {
        return ResponseEntity.ok(service.createOrUpdateCodeSystem(id, codeSystemJson));
    }

    @GetMapping(value = Constants.EndPoints.FHIR_API_CODE_SYNC_LINE_ITEMS)
    @Override
    public ResponseEntity<String> updateLineItemsHistory(String use, int count) {
        return ResponseEntity.ok(service.addLineItemsHistory(use, count));
    }

}
