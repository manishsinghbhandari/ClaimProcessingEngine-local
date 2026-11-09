package io.coredge.health.claims.models;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.Map;

@Data
@Builder
@Document(collection = "Claim_Update_Log")
public class ClaimUpdateLogs {

    @Id
    private String id;
    @Field("claimId")
    private String claimId;

    @Field("icd_code")
    private String username;

    @Field("updateTime")
    private Date updateTime;

    @Field("old_value")
    private Map<String , String> oldValue;

    @Field("new_value")
    private Map<String , String> newValue;

}
