package io.coredge.health.claims.models.master;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PractitionerDetailsResponse {

    private String regNo;
    private String fullNames;
    private String gender;
    private String mobileNo;
    private String idNumber;
    private String idType;
    private String cadre;
    private String qualifications;
    private String licenceNumber;
    private String email;
    private String specialty;
    private String subSpecialty;
    private String licenceType;
    private String postalAddress;
    private String disciplineName;
}
