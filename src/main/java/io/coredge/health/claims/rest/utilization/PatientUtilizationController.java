package io.coredge.health.claims.rest.utilization;

import io.coredge.health.claims.dto.utilization.PatientUtilizationResponse;
import io.coredge.health.claims.dto.utilization.PatientUtilizationResponseDto;
import io.coredge.health.claims.models.DropDown;
import io.coredge.health.claims.service.utilization.PatientUtilizationService;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/patient-utilization")
public class PatientUtilizationController {

    private final PatientUtilizationService service;

    public PatientUtilizationController(PatientUtilizationService service) {
        this.service = service;
    }

    @GetMapping("/{crId}/{code}")
    public ResponseEntity<List<PatientUtilizationResponse>> createUtilization(@PathVariable("crId") String crId, @PathVariable("code") String code)
     {
        return ResponseEntity.ok(service.getUtilizationBalance(crId,code));
    }

    @GetMapping("/benefits")
    public ResponseEntity<List<DropDown>> getSearchBenefits(@RequestParam
                                                                            @Size(min = 3, message = "Search Value must be atleast 3 characters")
                                                                            String searchValue) {
        return ResponseEntity.ok(service.getSearchBenefits(searchValue , true));
    }

    @GetMapping("/summary/{claimId}/{code}/{use}")
    public ResponseEntity<PatientUtilizationResponseDto> getUtilizationBalanceForIndividual(
            @PathVariable String claimId,
            @PathVariable String code,
            @PathVariable String use
    ) {
        return ResponseEntity.ok(service.getUtilizationBalanceForIndividual(claimId, code, use));
    }

}
