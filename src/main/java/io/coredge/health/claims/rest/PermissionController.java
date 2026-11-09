package io.coredge.health.claims.rest;

import io.coredge.health.claims.models.users.PermissionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

public interface PermissionController {
    ResponseEntity<PermissionResponse> getPermissions(@PathVariable String username);
}
