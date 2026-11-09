package io.coredge.health.claims.dto;

import java.util.UUID;

import io.coredge.health.claims.annotations.CheckUUID;
import io.coredge.health.claims.annotations.NotBlank;
import io.coredge.health.claims.annotations.ValidUUID;
import lombok.*;

//@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class ProcessClaimDTO {

    @NotBlank
    @CheckUUID
    private String claimId;

    @NotBlank
    private String benefitCode;

    @NotBlank
    private Double amount;

    @NotBlank
    private String schemeCode;

    public UUID getClaimId() {
        return claimId != null ? UUID.fromString(claimId) : null;
    }
}

