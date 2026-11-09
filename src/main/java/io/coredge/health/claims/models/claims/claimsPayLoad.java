package io.coredge.health.claims.models.claims;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class claimsPayLoad {
    @NotNull(message = "claimId cannot be null")
    private UUID claimId;

    @NotBlank(message = "message cannot be blank or null")
    private String message;
}
