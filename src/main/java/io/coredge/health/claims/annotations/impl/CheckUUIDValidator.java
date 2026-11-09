package io.coredge.health.claims.annotations.impl;

import io.coredge.health.claims.annotations.CheckUUID;
import org.springframework.util.StringUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class CheckUUIDValidator implements ConstraintValidator<CheckUUID, String> {
    String parttern = "[0-9abcdef-]{36}";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return StringUtils.isEmpty(value) || Pattern.compile(parttern).matcher(value).matches();
    }
}
