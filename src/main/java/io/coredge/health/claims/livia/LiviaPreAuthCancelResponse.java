package io.coredge.health.claims.livia;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for pre-authorization cancellation from Livia system.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LiviaPreAuthCancelResponse {

    private int statusCode;

    private String message;

    private String status;

    private String error;
}
