package io.coredge.health.claims.rest;

import static io.coredge.health.claims.constants.Constants.EndPoints.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.coredge.health.claims.models.DiagnosisResponse;
import io.coredge.health.claims.service.DiagnosisService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(DIAGNOSIS_API_PATH)
@Slf4j
public class DiagnosisController {

  @Autowired
  private DiagnosisService diagnosisService;

  @GetMapping
  public ResponseEntity<List<DiagnosisResponse>> getDiagnosisByCode(
      @RequestParam String diagnosisCode) throws JsonProcessingException {
    return ResponseEntity.ok(diagnosisService.fetchDiagnosis(diagnosisCode));
  }

  @GetMapping(DIAGNOSIS_CLAIMID_API_PATH)
  public ResponseEntity<List<DiagnosisResponse>> getDiagnosisByClaimID(@PathVariable String claimId)
      throws JsonProcessingException {
    return ResponseEntity.ok(diagnosisService.fetchDiagnosisByClaimID(claimId));
  }

  @PostMapping
  public ResponseEntity<String> postDiagnosisByClaimObject(@RequestBody String claimObject)
      throws JsonProcessingException {
    return diagnosisService.saveDiagnosis(claimObject);
  }
}
