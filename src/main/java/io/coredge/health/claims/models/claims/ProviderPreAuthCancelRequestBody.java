package io.coredge.health.claims.models.claims;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProviderPreAuthCancelRequestBody {
    @NotBlank(message = "PreauthId is required")
    private String preAuthId;
    @NotBlank(message = "Requester is required")
    private String requester;
    @NotBlank(message = "Designation is required")
    private String designation;
    @NotBlank(message = "Cancellation Reason is required")
    private String cancellationReason;
    private ExtensionStateCodeEnum action;
}