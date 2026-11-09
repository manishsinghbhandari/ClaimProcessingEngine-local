package io.coredge.health.claims.payments.controller;

import io.coredge.health.claims.payments.service.PaymentBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentReparationController {

    private final PaymentBatchService paymentBatchService;

    /**
     * Endpoint to manually trigger the re-processing of claims marked as
     * NEEDS_REPARATION.
     * These claims typically failed due to pre-validation errors or 400 Gateway
     * rejections.
     */
    @PostMapping("/reprocess-reparation")
    public ResponseEntity<Map<String, String>> reprocessReparationClaims() {
        log.info("API call received to re-process reparation claims.");

        // Trigger the reparation processing asynchronously or synchronously.
        // Given the scale, synchronous is fine for now as it's a manual trigger.
        paymentBatchService.reprocessReparationClaims();

        return ResponseEntity.ok(Map.of(
                "message", "Reparation processing job triggered successfully",
                "status", "Job Started"));
    }


    /**
     * Endpoint to manually trigger the ERP re-processing of claims marked as
     * ERP NEEDS_REPARATION.
     */
    @PostMapping("/reprocess-erpreparation")
    public ResponseEntity<Map<String, String>> reprocessErpReparationClaims() {
        log.info("API call received to re-process erp reparation claims.");

        // Trigger the reparation processing asynchronously or synchronously.
        // Given the scale, synchronous is fine for now as it's a manual trigger.
        paymentBatchService.reprocessErpReparationClaims();

        return ResponseEntity.ok(Map.of(
                "message", "Reparation processing job triggered successfully",
                "status", "Job Started"));
    }
}
