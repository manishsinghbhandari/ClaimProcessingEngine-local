package io.coredge.health.claims.models.benefits;

import lombok.*;

import java.util.List;

@NoArgsConstructor // Required for Jackson to deserialize JSON
@AllArgsConstructor // Required for @Builder and full constructor usage
@Builder // Enables Builder pattern
@Getter // Generates getters
@Setter // Generates setters
public class BenefitsMasterResponseDTO {

    private Long id;
    private String interventionCode;
    private String code;
    private String accessCode;
    private String gender;
    private Boolean needsPreAuth;
    private String providerPaymentMechanism;
    private String limitsIndividualHousehold;
    private String interventionQuantityYear;
    private String appliesToSchemes;
    private String tariffs;
    private Integer level2Tariff;
    private Integer level3Tariff;
    private Integer level4Tariff;
    private Integer level5Tariff;
    private Integer level6Tariff;
    private String bedType;
    private List<String> documentTypes;
    private List<String> aiValidationScore;
    private Boolean efwaCheckEnabled;
    private Long routeId;
    private String routeName;
}