package io.coredge.health.claims.models.cpp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestCppProviders {
    private String providerName;
    private String providerType;
    private String level;
    private String registrationNumber;
    private String sladeCode;
    private String frCode;
    private String county;
    private String countyCode;
    private String facilityOwnership;
    private String contactPerson;
    private String emailId;
    private String contactNumber;
    private String providerAddress;

}
