package io.coredge.health.claims.dto.eligibility;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Finsprint eligibility validation API v5
 * Supports multiple schemes per member
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinsprintEligibilityResponse {

    @JsonProperty("requestIdType")
    private Integer requestIdType;
    
    @JsonProperty("requestIdNumber")
    private String requestIdNumber;
    
    @JsonProperty("memberCrNumber")
    private String memberCrNumber;
    
    @JsonProperty("fullName")
    private String fullName;
    
    @JsonProperty("statusCode")
    private String statusCode;
    
    @JsonProperty("statusDesc")
    private String statusDesc;
    
    @JsonProperty("schemes")
    private List<Scheme> schemes;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scheme {
        @JsonProperty("schemeName")
        private String schemeName;
        
        @JsonProperty("schemeId")
        private Integer schemeId;
        
        @JsonProperty("memberType")
        private String memberType;
        
        @JsonProperty("policy")
        private Policy policy;
        
        @JsonProperty("coverage")
        private Coverage coverage;
        
        @JsonProperty("principalContributor")
        private PrincipalContributor principalContributor;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Policy {
        @JsonProperty("startDate")
        private String startDate;
        
        @JsonProperty("endDate")
        private String endDate;
        
        @JsonProperty("number")
        private String number;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coverage {
        @JsonProperty("startDate")
        private String startDate;
        
        @JsonProperty("endDate")
        private String endDate;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("reason")
        private String reason;
        
        @JsonProperty("possibleSolution")
        private String possibleSolution;
        
        @JsonProperty("status")
        private String status;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrincipalContributor {
        @JsonProperty("idNumber")
        private String idNumber;
        
        @JsonProperty("idType")
        private String idType;
        
        @JsonProperty("crNumber")
        private String crNumber;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("relationship")
        private String relationship;
        
        @JsonProperty("employmentType")
        private String employmentType;
        
        @JsonProperty("employerDetails")
        private EmployerDetails employerDetails;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployerDetails {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("jobGroup")
        private String jobGroup;
    }
}
