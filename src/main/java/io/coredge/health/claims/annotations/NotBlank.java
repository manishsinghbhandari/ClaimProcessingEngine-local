package io.coredge.health.claims.annotations;
import io.coredge.health.claims.annotations.impl.NotBlankValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static io.coredge.health.claims.constants.Constants.DeveloperMessage.FIELD_REQUIRED;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Constraint(validatedBy = NotBlankValidator.class)
@Target({TYPE, FIELD, PARAMETER})
public @interface NotBlank {
    Class<?>[] groups() default {};

    String message() default FIELD_REQUIRED;

    Class<? extends Payload>[] payload() default {};
}