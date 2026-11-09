package io.coredge.health.claims.rest;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.models.ClaimHistoryDTO;
import io.coredge.health.claims.models.PatientResponse;
import io.coredge.health.claims.models.master.SuggestionResponse;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

public interface PatientController {

    @GetMapping(path = Constants.EndPoints.PATIENT_API_MAPPING_ALL_PAGE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CollectionModel<EntityModel<PatientResponse>>> fetchAllPageablePatients(@RequestParam(required = false) String gender, @RequestParam(required = false) String status,
                                                                                           @RequestParam(required = false) String name, @RequestParam(required = false) String identifier, int page, int size, String sortBy, String direction);

    @GetMapping(path = Constants.EndPoints.PATIENT_API_HISTORY_BY_PATIENT_ID, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CollectionModel<EntityModel<ClaimHistoryDTO>>> getPatientHistory(@PathVariable String patientId, @RequestParam(required = false) String shaCode, @RequestParam(defaultValue = "0") int page , @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "DESC") String direction);

    @GetMapping(path = Constants.EndPoints.PATIENT_API_MAPPING_SEARCH, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<SuggestionResponse>> searchAllPatient(String value);

    @GetMapping(path = Constants.EndPoints.PATIENT_API_HISTORY_BY_HOUSEHOLD, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CollectionModel<EntityModel<ClaimHistoryDTO>>> getPatientHistoryByHouseHold(@RequestParam("houseHoldId") String houseHoldId, @RequestParam(value = "claimNo", required = false) Long claimNo, @RequestParam(required = false) String shaCode, @RequestParam(defaultValue = "0") int page , @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "DESC") String direction);

    @GetMapping(path = Constants.EndPoints.PATIENT_API_GET_BY_ID, produces = MediaType.APPLICATION_JSON_VALUE)
     ResponseEntity<PatientResponse> getPatientById(@PathVariable String claimId);

    @PutMapping(path = Constants.EndPoints.PATIENT_API_UPDATE_EXTERNAL, produces = MediaType.APPLICATION_JSON_VALUE)
     ResponseEntity<PatientResponse> updatePatientData(@PathVariable String patientId, @RequestBody Map<String, Object> patientData);
}
