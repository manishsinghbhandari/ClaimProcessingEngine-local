package io.coredge.health.claims.models.benefits;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BenefitsMasterRequestDTO {

    private String accessCode;
    private String gender;
    private Boolean needsPreAuth;
    private String providerPaymentMechanism;
    private String limitsIndividualHousehold;
    private String interventionQuantityYear;
    private String appliesToSchemes;
    private String bedType;

    @NotEmpty(message = "Document types should not be empty")
    private List<Long> documentTypes; // document types

    @NotEmpty(message = "AI types should not be empty")
    private List<Long> aiValidationScore; //

    /**
     * tariffs is varchar in DB; validate numeric & non-negative at API layer.
     * Accept Integer here, then store canonical string.
     */
    @Pattern(regexp = "^[0-9]+(\\.[0-9]+)?$", message = "tariffs must be must be >= 0")
    private String tariffs;

    @Min(value = 0, message = "level2Tariff must be >= 0")
    private Integer level2Tariff;

    @Min(value = 0, message = "level3Tariff must be >= 0")
    private Integer level3Tariff;

    @Min(value = 0, message = "level4Tariff must be >= 0")
    private Integer level4Tariff;

    @Min(value = 0, message = "level5Tariff must be >= 0")
    private Integer level5Tariff;

    @Min(value = 0, message = "level6Tariff must be >= 0")
    private Integer level6Tariff;

    private Boolean efwaCheckEnabled;
    private Long routeId;
}