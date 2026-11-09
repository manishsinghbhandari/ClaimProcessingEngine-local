package io.coredge.health.claims.models.report;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "Claim_Review_Summary")
public class ClaimReviewSummary {

    @Id
    private String id;
    private ExtensionStateCodeEnum status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String fileName;
    private String base64File;
}
