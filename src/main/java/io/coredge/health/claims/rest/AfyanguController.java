package io.coredge.health.claims.rest;

import io.coredge.health.claims.service.AfyanguService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import static io.coredge.health.claims.constants.Constants.EndPoints.AFYANGU;

@RestController
@RequestMapping(AFYANGU)
@Slf4j
public class AfyanguController {

    @Autowired
    private AfyanguService afyanguService;

    @PostMapping()
    public ResponseEntity<?> getClaimRules() {
        afyanguService.sendWebhookAsyncJob();
        return ResponseEntity.accepted().build();
    }

}
