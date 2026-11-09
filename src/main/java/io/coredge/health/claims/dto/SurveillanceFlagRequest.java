package io.coredge.health.claims.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveillanceFlagRequest {

    @NotBlank(message = "Claim ID is required")
    private String claimId;

    @NotBlank(message = "Reason is required")
    private String reason;

    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Description is required")
    private String description;


    private Boolean changeStatus;
}
