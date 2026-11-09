package io.coredge.health.claims.models.eclaims;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class ClaimProcessInterventionData {

    public Map<Integer, InterventionCodeDetail> interventionDetails;
    public Map<String, InterventionCodeDetail> interventionCodeDetailsStr;

    public boolean hasInterventionDetails(){
        return interventionDetails == null || !interventionDetails.isEmpty();
    }
    public boolean hasInterventionCodeDetails(){
        return interventionCodeDetailsStr == null || !interventionCodeDetailsStr.isEmpty();
    }


    @Data
    @Builder
    public static class InterventionCodeDetail {
        private String interventionCode;
        private BigDecimal approvedAmount;
        private Integer sequence;
        private  Map<String, BigDecimal> interventionAmountPercentageShare;
    }
}
