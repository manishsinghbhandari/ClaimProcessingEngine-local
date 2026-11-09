package io.coredge.health.claims.models.redis;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Data
@Builder
@Document(collection = "Claim_ICD_Relation")
public class ClaimICDRelation {
    @Id
    private String id;
    private String claimId;

    @Field("diagnosis")
    private List<Diagnosis> diagnosis;

    @Data
    public static class Diagnosis {

        @Field("icd_code")
        private String icdCode;

        @Field("icd_system")
        private String icdSystem;
    }
}
