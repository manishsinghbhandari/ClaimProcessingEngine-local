package io.coredge.health.claims.rest;

import io.coredge.health.claims.service.EligibilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.coredge.health.claims.constants.Constants.EndPoints.CLAIMS_API_MAPPING_ELIGIBILITY;
import static io.coredge.health.claims.constants.Constants.EndPoints.CLAIMS_API_PATH;

@RestController
@RequestMapping(CLAIMS_API_PATH)
@Slf4j
public class EligibilityController {

    @Autowired
    private EligibilityService eligibilityService;

    @PostMapping(path = CLAIMS_API_MAPPING_ELIGIBILITY, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> checkEligibility(@RequestBody String eligibilityJson) {
        return ResponseEntity.ok(eligibilityService.checkEligibilityS(eligibilityJson));
    }
}
