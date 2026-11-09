package io.coredge.health.claims.models.AI;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRouteDTO {

    private Long id;

    @NotBlank(message = "Route name is required")
    private String name;

    @NotBlank(message = "API endpoint is required")
    private String apiEndpoint;

    @Builder.Default
    private Boolean isDefault = false;
}
