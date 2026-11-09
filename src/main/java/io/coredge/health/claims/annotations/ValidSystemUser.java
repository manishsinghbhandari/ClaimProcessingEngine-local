package io.coredge.health.claims.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to validate that the API request is from a 'system' source only.
 * Apply to controller methods to restrict access to system API keys.
 * 
 * Usage:
 * <pre>
 * {@code @ValidSystemUser}
 * {@code @PostMapping("/internal/process")}
 * public ResponseEntity<?> internalProcess() {
 *     // Only accessible by system API keys
 * }
 * </pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSystemUser {
}
