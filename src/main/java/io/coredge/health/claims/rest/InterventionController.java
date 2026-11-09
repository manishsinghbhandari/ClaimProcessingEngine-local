package io.coredge.health.claims.rest;

import io.coredge.health.claims.service.ClaimAdjudicationService;
import io.coredge.health.claims.service.InterventionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static io.coredge.health.claims.constants.Constants.AdminEndPoints.INTERVENTION_IMPORT_API_MAPPING;
import static io.coredge.health.claims.constants.Constants.AdminEndPoints.INTERVENTION_API_PATH;

@RestController
@RequestMapping(INTERVENTION_API_PATH)
@Slf4j
public class InterventionController {


    @Autowired
    private InterventionService interventionService;


    @GetMapping(INTERVENTION_IMPORT_API_MAPPING)
    public ResponseEntity<String> adjudication(String claimResponseID) {
        try {
            interventionService.importInterventions();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok("");
    }
}
