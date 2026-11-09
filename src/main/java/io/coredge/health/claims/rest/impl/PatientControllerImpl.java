package io.coredge.health.claims.rest.impl;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.ClaimHistoryDTO;
import io.coredge.health.claims.models.PatientResponse;
import io.coredge.health.claims.models.master.SuggestionResponse;
import io.coredge.health.claims.rest.PatientController;
import io.coredge.health.claims.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(Constants.EndPoints.PATIENT_API_MAPPING)
public class PatientControllerImpl implements PatientController {

    @Autowired
    private MemberService memberService;

    @Override
    public ResponseEntity<CollectionModel<EntityModel<PatientResponse>>> fetchAllPageablePatients(@RequestParam(required = false) String gender, @RequestParam(required = false) String status,
    @RequestParam(required = false) String name, @RequestParam(required = false) String identifier, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "ASC") String direction) {
        return ResponseEntity.ok(memberService.fetchAllPatientDetailsPage(gender, status, name, identifier, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, true))));
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<ClaimHistoryDTO>>> getPatientHistory(String patientId , String shaCode, int page,int size, String sortBy, String direction) {
        return ResponseEntity.ok(memberService.getPatientIndividualHistory(patientId, shaCode, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, true))));
    }

    @Override
    public ResponseEntity<List<SuggestionResponse>> searchAllPatient(@PathVariable String value) {
        return ResponseEntity.ok(memberService.searchAllPatient(value));
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<ClaimHistoryDTO>>> getPatientHistoryByHouseHold(String houseHoldId, Long claimNo, String shaCode, int page, int size, String sortBy, String direction) {
        return ResponseEntity.ok(memberService.getPatientHouseHoldHistory(houseHoldId , claimNo, shaCode, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, true))));
   }

    @Override
    public ResponseEntity<PatientResponse> getPatientById(String claimId) {
        return ResponseEntity.ok(memberService.getPatient(claimId));
    }

    @Override
    public ResponseEntity<PatientResponse> updatePatientData(String patientId, Map<String, Object> patientData) {
        return ResponseEntity.ok(memberService.updatePatient(patientId, patientData));
    }

}
