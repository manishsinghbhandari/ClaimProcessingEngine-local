package io.coredge.health.claims.models.AI;


import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AIValidationResponse {

    private Long id;
    private String name;
    private String validationKey;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDateFrom;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDateTo;

    private List<AIValidationScoreResponse> aiValidationScore;
}

