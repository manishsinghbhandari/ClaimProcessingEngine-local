package io.coredge.health.claims.models.redis;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Builder
@Document(collection = "claimsAsyncCollection")
public class Message {

    @Id
    private String id;
    private String claimId;
    private String content;
    private boolean isPriority;
    private String channel;
    private int retryCount;
    private List<String> exceptionsList;
}
