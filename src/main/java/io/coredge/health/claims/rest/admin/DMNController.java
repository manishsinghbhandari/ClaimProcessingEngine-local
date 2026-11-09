package io.coredge.health.claims.rest.admin;

import io.coredge.health.claims.service.DMNService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.coredge.health.claims.constants.Constants.AdminEndPoints.*;

@RestController
@RequestMapping(BPM_API_PATH)
@Slf4j
public class DMNController {

    @Autowired
    private DMNService dmnService;

    @GetMapping(BPM_DMN_GEN_API_MAPPING)
    public ResponseEntity<String> generateDmn(String claimResponseID) {
        return ResponseEntity.ok(dmnService.generateInterventionDMN("generateDecision.dmn", false));
    }

/*    @GetMapping(BPM_READ_DMN_API_MAPPING)
    public ResponseEntity<String> readDmn() {
        return ResponseEntity.ok(dmnService.claimDecisionRules());
    }*/
}
