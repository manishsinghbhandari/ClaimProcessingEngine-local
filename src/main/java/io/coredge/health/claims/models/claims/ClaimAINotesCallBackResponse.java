package io.coredge.health.claims.models.claims;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClaimAINotesCallBackResponse {
    private String status;
    private String message;
}
