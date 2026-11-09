package io.coredge.health.claims.rest.impl;

import io.coredge.health.claims.annotations.ValidSystemUser;
import io.coredge.health.claims.config.RedisRefreshBroadcaster;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for broadcasting config refresh to all application instances.
 */
@Slf4j
@RestController
@RequestMapping("/actuator")
@Tag(name = "Config Refresh", description = "Endpoints for refreshing application configuration")
public class RefreshController {

    private final RedisRefreshBroadcaster refreshBroadcaster;

    public RefreshController(RedisRefreshBroadcaster refreshBroadcaster) {
        this.refreshBroadcaster = refreshBroadcaster;
    }

    /**
     * Broadcasts a refresh signal to ALL instances via Redis Pub/Sub.
     * Use this endpoint instead of /actuator/refresh when running multiple instances.
     *
     * @return Response indicating the broadcast was sent
     */
    @PostMapping("/refresh-all")
    @Operation(summary = "Refresh all instances", 
               description = "Broadcasts a config refresh signal to all application instances via Redis")
    @ValidSystemUser
    public ResponseEntity<Map<String, String>> refreshAll() {
        log.info("Received request to refresh all instances");
        refreshBroadcaster.broadcastRefresh();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Refresh broadcast sent to all instances via Redis"
        ));
    }
}
