package io.coredge.health.claims.models.claims;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.coredge.health.claims.models.AI.ScoreDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClaimAINotesResponsePayload {
    private String claimId;
    private Map<String, ScoreDetail> scores;
    private ClaimAIDocumentNotes[] documentNotes;
    private String overallNotes;
    private String decision;
}
