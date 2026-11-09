package io.coredge.health.claims.models.intervention.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class ClaimUsage {

    private String interventionCode;

    // Surgical and Renal care  SHA-19, SHA-16-001
    private int totalUsedThisYear;
    private int totalUsedThisWeek;



    // For per diem overnight validation
    private LocalDate admissionDate;
    private LocalDate dischargeDate;

    // For Peritoneal Dialysis validation (30-day gap and yearly limit)
    private LocalDate lastUsageDate;
    private LocalDate currentClaimDate;

    // Inpatient for total used for this service Dialysis for acute kidney injury
    private int totalUsedThisAdmission;

    private Integer patientAge;

    private Double totalAmount;

    private String use;


}
