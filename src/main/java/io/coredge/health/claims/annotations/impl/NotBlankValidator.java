package io.coredge.health.claims.annotations.impl;


import io.coredge.health.claims.annotations.NotBlank;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

public class NotBlankValidator implements ConstraintValidator<NotBlank, Object> {
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        return (value instanceof String) ? Objects.nonNull(value) && value.toString().trim().length() > 0 : (value instanceof byte[]) ? Objects.nonNull(value) : Objects.nonNull(value) ? true : false;
    }
}