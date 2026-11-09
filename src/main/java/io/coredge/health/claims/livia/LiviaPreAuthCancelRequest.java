package io.coredge.health.claims.livia;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for cancelling a pre-authorization in Livia system.
 * Used when a civil servant preauth is cancelled to notify Livia.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LiviaPreAuthCancelRequest {

    private String preAuthReferenceNo;

    private String reason;
}
