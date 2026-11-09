package io.coredge.health.claims.models.redis;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "ICD_Codes")
public class ICDCode {
    @Id
    private String id;

    @Field("ICD 10 Disease Code")
    private String icd10DiseaseCode;

    @Field("Sub Code")
    private String subCode;

    @Field("Main code")
    private String mainCode;

    @Field("ICD 11 CODES")
    private String icd11Codes;

    @Field("Weight")
    private Double weight;
}
