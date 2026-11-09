package io.coredge.health.claims.models;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.coredge.health.claims.annotations.ValidUUID;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Predetermination {

    @JsonProperty("code")
    @Pattern(regexp = "^(?i)(SHA|PMF)-\\d{2}-\\d{3}$" , message = "Intervention Code is Invalid")
    @NotBlank(message = "SHA Code would not be Blank")
    private String code;

    @JsonProperty("claimId")
    @ValidUUID(message = "ClaimId is not a valid UUID")
//    @NotBlank(message = "ClaimId would not be Blank")
    private String claimId;

    @JsonProperty("patientId")
    @NotBlank(message = "Patient Id would not be Blank")
    private String patientId;

    @JsonProperty("gender")
    @Pattern(regexp = "^(?i)(male|female)$" , message = "Gender is Invalid")
    @NotBlank(message = "Gender would not be Blank")
    private String gender;

    @JsonProperty("birthDate")
    @NotNull(message = "BirthDate would not be Blank")
    @JsonFormat(shape = JsonFormat.Shape.STRING,lenient = OptBoolean.FALSE , pattern = "yyyy-MM-dd", timezone = "UTC")
    @PastOrPresent(message = "Birth Date would be in Past")
    private Date birthDate;

    @JsonProperty("billableStartDate")
    @NotNull(message = "Billable Start Date would not be Blank")
    @JsonFormat(shape = JsonFormat.Shape.STRING, lenient = OptBoolean.FALSE ,pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    private Date billableStartDate;

    @JsonProperty("billableEndDate")
    @NotNull(message = "Billable End Date would not be Blank")
    @JsonFormat(shape = JsonFormat.Shape.STRING, lenient = OptBoolean.FALSE ,pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    private Date billableEndDate;

    @JsonProperty("serviceDate")
//    @NotNull(message = "Service Date would not be Blank")
    private LocalDate serviceDate;

    @JsonProperty("claimAmount")
    @NotNull(message = "Claim Amount would not be Blank")
    private Double claimAmount;

    @JsonProperty("serviceCategory")
    @Pattern(regexp = "^(?i)(ip|op|emr|inpatient|outpatient|emergency)$" , message = "Service Category is Invalid  ")
    @NotBlank(message = "Service Category would not be Blank")
    private String serviceCategory;

    @JsonProperty("facilityCategory")
    @NotBlank(message = "Facility Category would not be Blank")
    private String facilityCategory;

    @JsonProperty("hasPreAuth")
    @NotNull(message = "Has PreAuth value would not be Blank")
    private Boolean hasPreAuth;

    @JsonProperty("providerId")
    @NotBlank(message = "ProviderId would not be Blank")
    private String providerId;

    @JsonProperty("sequence")
    private String sequence;

    @JsonProperty("householdNumber")
    private String householdNumber;

    @JsonProperty("preAuthRef")
    private String preAuthRef;

    @JsonProperty("documentAttachments")
    @NotEmpty(message = "Document attachments should not be empty")
    private List<String> documentAttachments = new ArrayList<>();

    public boolean hasPatientId() {
        return this.patientId != null && !this.patientId.isEmpty();
    }

    public boolean hasProviderId() {
        return this.providerId != null && !this.providerId.isEmpty();
    }

    public boolean hasPreAuthRef() {
        return this.preAuthRef != null && !this.preAuthRef.isEmpty();
    }

}
