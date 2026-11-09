package io.coredge.health.claims.models.claims;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClaimAIDocumentNotes {
    private String url;
    private String notes;
    private String score;
}
