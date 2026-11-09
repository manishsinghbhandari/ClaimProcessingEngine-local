package io.coredge.health.claims.models.claims;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.coredge.health.claims.models.AI.ScoreDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Payload structure for PreAuth AI callback response.
 * Example payload:
 * {
 * "claim_id": "bc5ad979-5917-4c06-84ab-7402b19bba75",
 * "scores": {
 * "preauthDecision": {
 * "score": "50%",
 * "remarks": ["ADDITIONAL INFORMATION REQUIRED..."]
 * }
 * },
 * "decision": "ADDITIONAL_INFORMATION_REQUIRED"
 * }
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PreAuthAIResponsePayload {

    @JsonProperty("claim_id")
    private String claimId;

    private Map<String, ScoreDetail> scores;

    private String decision;
}
