package io.coredge.health.claims.annotations;

import io.coredge.health.claims.annotations.impl.CheckUUIDValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static io.coredge.health.claims.constants.Constants.DeveloperMessage.UUID_MISSMATCH;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Constraint(validatedBy = CheckUUIDValidator.class)
@Target({TYPE, FIELD, PARAMETER})
public @interface CheckUUID {
    String message() default UUID_MISSMATCH;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
