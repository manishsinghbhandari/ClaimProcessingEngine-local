package io.coredge.health.claims.payments.controller;

import io.coredge.health.claims.payments.dto.ErpApprovalRequest;
import io.coredge.health.claims.payments.dto.PaymentGatewayCallbackRequest;
import io.coredge.health.claims.payments.dto.PaymentResponse;
import io.coredge.health.claims.payments.service.PaymentProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/payments/callback")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentProcessingService paymentProcessingService;

    @PostMapping("/erp")
    public ResponseEntity<PaymentResponse> handleErpApproval(@RequestBody ErpApprovalRequest request) {
        paymentProcessingService.processErpApproval(request);
        return ResponseEntity.ok(PaymentResponse.builder()
                .status("SUCCESS")
                .code("PAYMENT-200")
                .message("ERP Approval processed successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/gateway")
    public ResponseEntity<PaymentResponse> handleGatewayCallback(@RequestBody PaymentGatewayCallbackRequest request) {
        paymentProcessingService.processGatewayCallback(request);
        return ResponseEntity.ok(PaymentResponse.builder()
                .status("SUCCESS")
                .code("PAYMENT-200")
                .message("Gateway Callback processed successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }
}
