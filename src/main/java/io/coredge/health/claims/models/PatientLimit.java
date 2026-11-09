package io.coredge.health.claims.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class PatientLimit {
    private Integer individualUtilisedLimit;
    private Integer householdUtilisedLimit;
    private BigDecimal utilisedAmount;

    public PatientLimit(){
        individualUtilisedLimit = 0;
        householdUtilisedLimit = 0;
        utilisedAmount = BigDecimal.ZERO;
    }
}
