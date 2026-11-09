package io.coredge.health.claims.service.validation;

import io.coredge.health.claims.db.entity.InsuranceClaim;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Registry that auto-discovers all {@link PackageValidationStrategy} implementations
 * via Spring dependency injection. Provides a single entry point for BulkApprovalService
 * to check validation across all registered package strategies.
 *
 * <p>New strategies are automatically picked up — no changes needed here.</p>
 */
@Service
@Slf4j
public class ValidationStrategyRegistry {

    private final List<PackageValidationStrategy> strategies;

    public ValidationStrategyRegistry(List<PackageValidationStrategy> strategies) {
        this.strategies = strategies;
        log.info("Registered {} package validation strategies: {}", strategies.size(),
                strategies.stream().map(s -> s.getClass().getSimpleName()).toList());
    }

    /**
     * Check if any registered strategy handles the given intervention code.
     */
    public boolean hasValidation(String interventionCode) {
        return strategies.stream().anyMatch(s -> s.supports(interventionCode));
    }

    /**
     * Validate a claim against the matching strategy for the given intervention code.
     *
     * @param interventionCode the intervention code to match a strategy
     * @param claim            the insurance claim to validate
     * @return Optional containing rejection reason if validation fails, empty if valid
     */
    public Optional<String> validate(String interventionCode, InsuranceClaim claim) {
        return strategies.stream()
                .filter(s -> s.supports(interventionCode))
                .findFirst()
                .flatMap(s -> s.validate(claim));
    }
}
