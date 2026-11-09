package io.coredge.health.claims.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterventionDetails {
    @NotNull(message = "Sequence must not be null")
    private Integer sequence;

    private String interventionCode;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    private String reason;

    private Integer approvedLOS;

    private BigDecimal approvedAmount;
}