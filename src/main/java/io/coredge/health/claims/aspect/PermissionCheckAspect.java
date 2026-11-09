package io.coredge.health.claims.aspect;

import io.coredge.health.claims.annotations.RequiresPermission;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import io.coredge.health.claims.exception.app.UserNotAuthorizedException;
import io.coredge.health.claims.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class PermissionCheckAspect {

    @Autowired
    private PermissionService permissionService;

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        log.info("Checking permission for the Api Access.");
        ResourceEnum resource = requiresPermission.resource();
        ActionEnum[] actions = requiresPermission.action();  // Changed to handle multiple actions
        String username = HealthIdContextHolder.userId();
        log.info("User Active : {}", username);

        // Implement logic to validate user's permissions
        boolean hasPermission = checkUserPermission(username, resource, actions);
        log.info("Permission : {}", hasPermission);

        if (!hasPermission) {
            throw new UserNotAuthorizedException("Forbidden: Insufficient permissions");
        }
        return joinPoint.proceed();
    }

    private boolean checkUserPermission(String username, ResourceEnum resource, ActionEnum[] actions) {
        // Check if the user has permission for all the actions
        for (ActionEnum action : actions) {
            if (!permissionService.hasPermission(username, resource, action)) {
                return false;  // If a user lacks permission for any action, return false
            }
        }
        return true;  // User has permission for all actions
    }

}
