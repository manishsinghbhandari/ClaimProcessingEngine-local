package io.coredge.health.claims.rest.impl;


import io.coredge.health.claims.rest.PaymentsController;
import io.coredge.health.claims.service.PaymentsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static io.coredge.health.claims.constants.Constants.EndPoints.PAYMENT_API_PATH;
import static io.coredge.health.claims.constants.Constants.EndPoints.PAYMENT_NOTICE_MAPPING;

@Slf4j
@RestController
@RequestMapping(PAYMENT_API_PATH)
public class PaymentsControllerImpl implements PaymentsController {

    private final PaymentsService paymentsService;

    public PaymentsControllerImpl(PaymentsService paymentsService) {
        this.paymentsService = paymentsService;
    }

    @GetMapping(path = PAYMENT_NOTICE_MAPPING, produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public ResponseEntity<String> getCallBack(@RequestParam String claimId) {
        log.info("Payment Notice request, ClaimID: {} ", claimId);
        return ResponseEntity.ok(paymentsService.getPaymentsNotice(claimId));
    }
}
