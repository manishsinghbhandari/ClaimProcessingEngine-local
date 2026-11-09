package io.coredge.health.claims.rest.bms;

import io.coredge.health.claims.models.master.SuggestionResponse;
import io.coredge.health.claims.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static io.coredge.health.claims.constants.Constants.EndPoints.BMS_PATIENTS;
import static io.coredge.health.claims.constants.Constants.EndPoints.BMS_PATIENTS_SEARCH;

@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping(BMS_PATIENTS)
@RequiredArgsConstructor
public class PatientController {

    private final MemberService patientService; ;

    @GetMapping(BMS_PATIENTS_SEARCH)
    public List<SuggestionResponse> searchPatientsByName(@RequestParam String name) {
        return patientService.searchAllPatient(name);
    }
}