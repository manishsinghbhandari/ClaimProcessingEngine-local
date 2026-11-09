package io.coredge.health.claims.models;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClaimHistoryDTO {

    private String claimId;
    private Long claimNumber;
    private String providerName;
    private String patientName;
    private String interventionCode;
    private String interventionName;
    private LocalDate servicedDate;
    private Long quantity;
    private BigDecimal unitPrice;
    private BigDecimal netValue;
    private Integer approvedQuantity;
    private BigDecimal approvedPrice;
    private String justification;
    private ExtensionStateCodeEnum status;
    private Object adjudicationParams; // Use JsonNode to handle JSON objects
    private Integer sequence;
    private String reason;
    private BigDecimal pmfAmount;
    private boolean pmfItem;
    private BigDecimal shifAmount;
    private BigDecimal eccifAmount;
    private Constants.ClaimLineItemStatus lineItemStatus;
    private String preAuthId;
    private String claimType;


}
