package io.coredge.health.claims.models;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class RelatedInfo {

    private boolean isPreAuthRequired;
    private String preAuthId;
    private ExtensionStateCodeEnum preAuthStatus;
    private LocalDate preAuthExpiryDate;
}
