package io.coredge.health.claims.aspect;

import io.coredge.health.claims.annotations.ValidSystemUser;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.exception.app.UserNotAuthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Aspect that validates the request source is 'system' for methods
 * annotated with @ValidSystemUser.
 */
@Slf4j
@Aspect
@Component
public class SystemUserValidationAspect {

    private static final String SYSTEM_SOURCE = "system";

    @Around("@annotation(validSystemUser)")
    public Object validateSystemUser(ProceedingJoinPoint joinPoint, ValidSystemUser validSystemUser) throws Throwable {
        String requestSource = HealthIdContextHolder.requestSource();
        
        log.debug("Validating system user access. Request source: {}", requestSource);

        if (!SYSTEM_SOURCE.equalsIgnoreCase(requestSource)) {
            log.warn("Access denied. Expected source '{}' but got '{}'", SYSTEM_SOURCE, requestSource);
            throw new UserNotAuthorizedException("Forbidden: This endpoint is restricted to system API keys only");
        }

        log.debug("System user validation passed for source: {}", requestSource);
        return joinPoint.proceed();
    }
}
