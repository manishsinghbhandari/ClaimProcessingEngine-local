package io.coredge.health.claims.models.eclaims;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class InterventionCodeRequest {

    @JsonProperty("ServiceCodeType")
    private String ServiceCodeType;

    @JsonProperty("Amount")
    private BigDecimal Amount;
}