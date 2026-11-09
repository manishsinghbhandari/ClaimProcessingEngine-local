package io.coredge.health.claims.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static io.coredge.health.claims.constants.Constants.EndPoints.PAYMENT_NOTICE_MAPPING;

public interface PaymentsController {
    @GetMapping(PAYMENT_NOTICE_MAPPING)
    ResponseEntity<String> getCallBack(@RequestParam String claimId);
}
