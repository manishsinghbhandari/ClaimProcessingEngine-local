package io.coredge.health.claims.models;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Data
@Builder
public class PatientResponse {
    private UUID id;
    private String crId;
    private String nationalId;
    private String shaNumber;
    private String householdNumber;
    private String name;
    private String phoneNumber;
    private String gender;
    private Date birthDate;
    private String addressLine;
    private String city;
    private String district;
    private String state;
    private String county;
    private String country;

    private LocalDateTime policyStartDate;
    private LocalDateTime policyEndDate;
}