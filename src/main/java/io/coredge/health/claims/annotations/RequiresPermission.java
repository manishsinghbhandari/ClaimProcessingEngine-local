package io.coredge.health.claims.annotations;

import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    ResourceEnum resource();
    ActionEnum[] action();
}
