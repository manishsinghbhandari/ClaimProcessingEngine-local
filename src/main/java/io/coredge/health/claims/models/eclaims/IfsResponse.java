package io.coredge.health.claims.models.eclaims;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IfsResponse {

    @JsonProperty("@odata.context")
    private String odataContext;

    @JsonProperty("StatusCode")
    private String statusCode;

    @JsonProperty("StatusMessage")
    private String statusMessage;

}
