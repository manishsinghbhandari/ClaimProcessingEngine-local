package io.coredge.health.claims.models;

import io.coredge.health.claims.enums.BulkAction;
import io.coredge.health.claims.enums.SendbackStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "claim_bulk_send_back_logs")
public class ClaimBulkSendBackLogs {
    @Id
    private String id;
    private LocalDateTime timestamp;
    private String claimId;
    private SendbackStatus status; // SUCCESSFUL, FAILED, NOT_IN_DB
    private String messageFromUI;//From the Swagger UI
    private String errorMessage;//Stores Exception data
    private BulkAction action;
}
