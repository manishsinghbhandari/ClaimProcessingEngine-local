package io.coredge.health.claims.models.AI;

import io.coredge.health.claims.annotations.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BulkValidationMappingRequest {

    @NotBlank
    private String benefit;

    @NotEmpty
    private List<Long> validation;
}
