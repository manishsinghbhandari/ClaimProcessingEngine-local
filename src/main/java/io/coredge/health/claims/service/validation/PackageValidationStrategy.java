package io.coredge.health.claims.service.validation;

import io.coredge.health.claims.db.entity.InsuranceClaim;

import java.util.Optional;

/**
 * Strategy interface for package-specific claim validation during bulk approval.
 * Implement this interface and annotate with @Component to auto-register
 * with the ValidationStrategyRegistry.
 *
 * <p>Each strategy receives the full {@link InsuranceClaim} and extracts
 * whatever data it needs internally (claim items, civil servant status, etc.).</p>
 *
 * <p>Example: To add SHA-20 validation, create a new class:
 * <pre>
 * {@code @Component}
 * public class Sha20ValidationStrategy implements PackageValidationStrategy {
 *     public boolean supports(String code) { return code.startsWith("SHA-20"); }
 *     public Optional&lt;String&gt; validate(InsuranceClaim claim) { ... }
 * }
 * </pre>
 */
public interface PackageValidationStrategy {

    /**
     * Does this strategy handle the given intervention code?
     *
     * @param interventionCode the claim's intervention code
     * @return true if this strategy should validate claims with this code
     */
    boolean supports(String interventionCode);

    /**
     * Validate a claim against package-specific rules.
     * The strategy extracts all needed data (claim items, patient info, etc.) internally.
     *
     * @param claim the insurance claim to validate
     * @return Optional containing rejection reason if validation fails, empty if valid
     */
    Optional<String> validate(InsuranceClaim claim);
}
