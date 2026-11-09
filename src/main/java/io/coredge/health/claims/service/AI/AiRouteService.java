package io.coredge.health.claims.service.AI;

import io.coredge.health.claims.db.entity.AiRoute;
import io.coredge.health.claims.db.repositories.AiRouteRepository;
import io.coredge.health.claims.models.AI.AiRouteDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiRouteService {

    private final AiRouteRepository aiRouteRepository;

    /**
     * Get all AI routes.
     */
    public List<AiRouteDTO> findAll() {
        return aiRouteRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Create a new AI route.
     */
    @Transactional
    public AiRouteDTO create(AiRouteDTO dto) {
        AiRoute route = AiRoute.builder()
                .name(dto.getName())
                .apiEndpoint(dto.getApiEndpoint())
                .isDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false)
                .build();

        AiRoute saved = aiRouteRepository.save(route);
        log.info("Created AI route: {} with endpoint: {}", saved.getName(), saved.getApiEndpoint());
        return toDTO(saved);
    }

    /**
     * Toggle the default status of an AI route.
     * When setting isDefault=true, all other routes are set to isDefault=false.
     */
    @Transactional
    public AiRouteDTO toggleDefault(Long id, Boolean isDefault) {
        AiRoute route = aiRouteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AI Route not found with id: " + id));

        // If setting this route as default, disable all other defaults first
        if (Boolean.TRUE.equals(isDefault)) {
            aiRouteRepository.findAll().forEach(r -> {
                if (!r.getId().equals(id) && Boolean.TRUE.equals(r.getIsDefault())) {
                    r.setIsDefault(false);
                    aiRouteRepository.save(r);
                    log.info("Disabled default for AI route '{}'", r.getName());
                }
            });
        }

        route.setIsDefault(isDefault);
        AiRoute saved = aiRouteRepository.save(route);
        log.info("Updated AI route '{}' isDefault to: {}", saved.getName(), isDefault);
        return toDTO(saved);
    }

    private AiRouteDTO toDTO(AiRoute entity) {
        return AiRouteDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .apiEndpoint(entity.getApiEndpoint())
                .isDefault(entity.getIsDefault())
                .build();
    }
}
