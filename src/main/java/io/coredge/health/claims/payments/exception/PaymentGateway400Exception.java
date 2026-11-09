package io.coredge.health.claims.payments.exception;

public class PaymentGateway400Exception extends RuntimeException {
    public PaymentGateway400Exception(String message) {
        super(message);
    }
}
