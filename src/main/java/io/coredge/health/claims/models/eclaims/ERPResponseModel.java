package io.coredge.health.claims.models.eclaims;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ERPResponseModel implements Serializable {

    @JsonProperty("status_code")
    private String statusCode;

    @JsonProperty("response_status")
    private String responseStatus;

    @JsonProperty("response_body")
    private ResponseBody responseBody;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResponseBody {
        @JsonProperty("result")
        private Result result;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class Result {
            @JsonProperty("status_code")
            private String statusCode;

            @JsonProperty("status")
            private String status;

            @JsonProperty("message")
            private String message;

            @JsonProperty("claim_id")
            private String claimId;

            @JsonProperty("id")
            private String id;

            @JsonProperty("ids")
            private List<String> ids;

            @JsonProperty("missing_fields")
            private List<String> missingFields;
        }
    }
}
