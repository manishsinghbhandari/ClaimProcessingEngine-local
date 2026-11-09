package io.coredge.health.claims.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Ignore null values during serialization
public class RuleInputDTO {
    private Object rule;
    private String ruleSummary;
    private String reason;
    private Object input;
    private String result;
    private BigDecimal shifAmount;
    private BigDecimal pmsfAmount;
    private BigDecimal eccifAmount;
}
