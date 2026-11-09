package io.coredge.health.claims.exception.benefits;

/**
 * Exception thrown when AI configuration validation fails.
 */
public class AiConfigurationException extends RuntimeException {

    public AiConfigurationException(String message) {
        super(message);
    }
}
