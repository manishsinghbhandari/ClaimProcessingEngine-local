package io.coredge.health.claims.aspect;

import io.coredge.health.claims.context.HealthIdContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;


@ConditionalOnExpression("${enable.aop.logging:false}")
@Aspect
@Configuration
@Slf4j
public class LoggingAspect {

    private static final String AFTER_LOGS = "[{}] - {} - Finished execution for the request.";
    private static final String BEFORE_LOGS = "[{}] - {} - Starts execution for the request.";

    @Pointcut("execution(* io.coredge.health.claims.rest.*.*(..))")
    public void controllerLayer() {}

    @Pointcut("execution(* io.coredge.health.claims.service.*.*(..))")
    public void serviceLayer() {}

    @Pointcut("execution(* io.coredge.health.claims.zeebe.*.*(..))")
    public void zeebeLayer() {}

    @Before("controllerLayer()")
    public void beforeController(JoinPoint joinPoint) {
        uniqueRequestId();
        if (isExcludedMethod(joinPoint)) {
            printLogs(BEFORE_LOGS, joinPoint);
        }
    }

    @After("controllerLayer()")
    public void afterController(JoinPoint joinPoint) {
        if (isExcludedMethod(joinPoint)) {
            printLogs(AFTER_LOGS, joinPoint);
        }
        HealthIdContextHolder.remove();
    }

    @Before("serviceLayer() && !zeebeLayer()")
    public void beforeService(JoinPoint joinPoint) {
        if (isExcludedMethod(joinPoint)) {
            printLogs(BEFORE_LOGS, joinPoint);
        }
    }

    @After("serviceLayer() && !zeebeLayer()")
    public void afterService(JoinPoint joinPoint) {
        if (isExcludedMethod(joinPoint)) {
            printLogs(AFTER_LOGS, joinPoint);
        }
    }

    private void printLogs(String message, JoinPoint joinPoint) {
        String methodSignature = joinPoint.getSignature().toString(); // Full method signature
        // Log only in DEBUG mode for excessive logs
            log.debug(message, HealthIdContextHolder.uniqueRequestId(), methodSignature);
    }

    private String uniqueRequestId() {
        String uniqueRequestId = HealthIdContextHolder.uniqueRequestId();
        if (StringUtils.isEmpty(uniqueRequestId)) {
            uniqueRequestId = String.valueOf(Thread.currentThread().threadId())
                    .concat("-")
                    .concat(String.valueOf(System.currentTimeMillis()))
                    .concat("-USER:")
                    .concat(HealthIdContextHolder.userId());
            HealthIdContextHolder.uniqueRequestId(uniqueRequestId);
            return uniqueRequestId;
        }
        return uniqueRequestId;
    }

    // Helper method to exclude specific methods from logging
    private boolean isExcludedMethod(JoinPoint joinPoint) {
        String methodSignature = joinPoint.getSignature().toString();
        // Check if the method is 'isExistByJobKey' and exclude it from logging
        return !methodSignature.contains("isExistByJobKey(Long)");
    }
}
