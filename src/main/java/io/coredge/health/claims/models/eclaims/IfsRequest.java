package io.coredge.health.claims.models.eclaims;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class IfsRequest {
    @JsonProperty("Invoices")
    private Invoice invoices;
}
