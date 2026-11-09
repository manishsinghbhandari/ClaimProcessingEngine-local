package io.coredge.health.claims.rest.impl;

import io.coredge.health.claims.models.users.PermissionResponse;
import io.coredge.health.claims.rest.PermissionController;
import io.coredge.health.claims.service.KeycloakUserService;
import io.coredge.health.claims.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.coredge.health.claims.constants.Constants.EndPoints.PERMISSIONS_API_BY_USERNAME;
import static io.coredge.health.claims.constants.Constants.EndPoints.PERMISSIONS_API_MAPPING;


@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping(PERMISSIONS_API_MAPPING)
public class PermissionControllerImpl implements PermissionController {

    @Autowired
    private KeycloakUserService keycloakUserService;

    @Autowired
    private PermissionService permissionService;

    @GetMapping(PERMISSIONS_API_BY_USERNAME)
    @Override
    public ResponseEntity<PermissionResponse> getPermissions(@PathVariable String username) {
        return ResponseEntity.ok(permissionService.getPermissionsByUsername(username));
    }


}
