package io.coredge.health.claims.rest;

import io.coredge.health.claims.service.ClaimAdjudicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.coredge.health.claims.constants.Constants.EndPoints.ADJUDICATION_API_MAPPING;
import static io.coredge.health.claims.constants.Constants.EndPoints.ADJUDICATION_API_PATH;

@RestController
@RequestMapping(ADJUDICATION_API_PATH)
@Slf4j
public class AdjudicationController {


    @Autowired
    private ClaimAdjudicationService claimAdjudicationService;


    @GetMapping(ADJUDICATION_API_MAPPING)
    public ResponseEntity<String> adjudication(String claimResponseID) {
        return ResponseEntity.ok(claimAdjudicationService.getAdjudicationFromClaimResponse(claimResponseID));
    }
}
