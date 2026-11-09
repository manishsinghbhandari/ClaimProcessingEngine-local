package io.coredge.health.claims.rest.AI;

import io.coredge.health.claims.models.AI.AiRouteDTO;
import io.coredge.health.claims.service.AI.AiRouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static io.coredge.health.claims.constants.Constants.EndPoints.*;

@RestController
@RequestMapping(AI_ROUTE_BASE_ENDPOINT)
@RequiredArgsConstructor
@CrossOrigin
public class AiRouteController {

    private final AiRouteService aiRouteService;

    /**
     * Get all AI routes.
     */
    @GetMapping
    public ResponseEntity<List<AiRouteDTO>> getAllRoutes() {
        return ResponseEntity.ok(aiRouteService.findAll());
    }

    /**
     * Create a new AI route.
     */
    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<String> createRoute(@Valid @RequestBody AiRouteDTO dto) {
        aiRouteService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body("AI Route created successfully");
    }

    /**
     * Toggle the default status of an AI route.
     */
    @PatchMapping(AI_ROUTE_ID)
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<String> toggleRouteDefault(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean isDefault = body.get("isDefault");
        if (isDefault == null) {
            return ResponseEntity.badRequest().body("isDefault field is required");
        }
        aiRouteService.toggleDefault(id, isDefault);
        return ResponseEntity.ok("AI Route updated successfully");
    }
}
