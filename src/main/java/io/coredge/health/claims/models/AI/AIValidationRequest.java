package io.coredge.health.claims.models.AI;


import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AIValidationRequest {

    @NotNull
    private String name;

    @NotNull
    private String validationKey;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDateFrom;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDateTo;

    private List<AIValidationScoreRequest> aiValidationScore;

}
