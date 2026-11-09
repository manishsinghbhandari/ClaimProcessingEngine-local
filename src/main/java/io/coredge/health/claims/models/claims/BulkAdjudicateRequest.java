package io.coredge.health.claims.models.claims;

import io.coredge.health.claims.enums.BulkAction;
import io.coredge.health.claims.enums.ClaimType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkAdjudicateRequest {

    @NotNull(message = "Action cannot be null")
    private BulkAction action;

    @NotNull(message = "Claim type cannot be null")
    private ClaimType claimType;

    @NotEmpty(message = "Claims cannot be null or empty")
    List<@Valid claimsPayLoad> claims;
}
