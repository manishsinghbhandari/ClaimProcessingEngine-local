package io.coredge.health.claims.db.entity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.coredge.health.claims.db.entity.BMS.JobGroup;
import io.coredge.health.claims.db.entity.BMS.MemberRelation;
import io.coredge.health.claims.enums.IdentifierEnum;
import io.coredge.health.claims.exception.app.DataValidationException;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@Table(name = "patient", schema = "public")
public class Member extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cr_id", unique = true, nullable = false)
    private String crId;

    @Column(name = "national_id", unique = true)
    private String nationalId;

    @Column(name = "sha_number", unique = true)
    private String shaNumber;

    @Column(name = "household_number")
    private String householdNumber;

    @Column(name = "name")
    private String name;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "gender")
    private String gender;

    @Column(name = "birth_date")
    private Date birthDate;

    @Column(name = "birth_certificate")
    private String birthCertificate;

    @Column(name = "address_line")
    private String addressLine;

    @Column(name = "city")
    private String city;

    @Column(name = "district")
    private String district;

    @Column(name = "state")
    private String state;

    @Column(name = "county")
    private String county;

    @Column(name = "country")
    private String country;

    @Column(name = "disability_status")
    private  String disabilityStatus;

    @Column(name = "wallet_id")
    private String walletId;

    @Column(name = "marital_status")
    private String maritalStatus;

    @Column(name = "coverage_start_date")
    private LocalDate coverageStartDate;

    @Column(name = "coverage_end_date")
    private LocalDate coverageEndDate;

    @Column(name = "policy_start_date")
    private LocalDateTime policyStartDate;

    @Column(name = "policy_end_date")
    private LocalDateTime policyEndDate;

//    @Column(name = "country")
//    private String emergencyContactName;
//
//    @Column(name = "country")
//    private String emergencyContactRelationship;


    public void setCrId(String crId){
        if(crId.contains("Patient/")){
            this.crId = crId.split("Patient/")[1];
        }else{
            this.crId = crId;
        }
    }


    // Populate fields from FHIR Patient
    public static Member fromFhirPatient( Member member , Patient fhirPatient) {
        Member entity =null;
        entity = Objects.requireNonNullElseGet(member, Member::new);
        // Set the FHIR patient ID
        entity.setCrId(fhirPatient.getIdElement().getIdPart());

        // Extract identifiers based on system and code
        for (Identifier identifier : fhirPatient.getIdentifier()) {
            String system = identifier.getSystem();
            String value = identifier.getValue();

            if (system.contains(IdentifierEnum.NATIONAL_ID.getIdentifier())) {
                entity.nationalId = value;
            } else if (system.contains(IdentifierEnum.SHA_NUMBER.getIdentifier())) {
                entity.shaNumber = value;
            } else if (system.contains(IdentifierEnum.HOUSEHOLD_NUMBER.getIdentifier())) {
                entity.householdNumber = value;
            } else if (system.contains(IdentifierEnum.PHONE_NUMBER.getIdentifier())) {
                entity.phoneNumber = value;
                // Add more cases as needed
            }
        }

        // Extract name
        Optional<HumanName> nameOptional = fhirPatient.getName().stream().findFirst();
        entity.name = nameOptional.map(HumanName::getText).orElse(null);

        if (StringUtils.hasText(entity.phoneNumber)){
            // Extract phone number
            Optional<ContactPoint> phoneOptional = fhirPatient.getTelecom().stream()
                    .filter(contact -> "phone".equals(contact.getSystem().toCode()))
                    .findFirst();
        entity.phoneNumber = phoneOptional.map(ContactPoint::getValue).orElse(null);
    }
        // Extract gender
        entity.gender = fhirPatient.getGender() != null ? fhirPatient.getGender().toCode() : null;

        // Extract birthDate
        entity.birthDate = fhirPatient.getBirthDate();

        // Extract extension for county
        Member finalEntity = entity;
        fhirPatient.getExtension().stream()
                .filter(ext -> "county".contains(ext.getUrl()))
                .findFirst()
                .ifPresent(ext -> finalEntity.county = ext.getValueAsPrimitive().getValueAsString());

        return entity;
    }

    public static Member fromJsonBundle(String json) {
        try {
            log.info("Processing Patient fromJsonBundle to Member Object");
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode entryNode = objectMapper.readTree(json);
            Member member = new Member();

            // Set the FHIR patient ID
            member.setCrId(entryNode.path("id").asText(null));

            // Extract identifiers
            JsonNode identifiers = entryNode.path("identifier");
            if (identifiers.isArray()) {
                for (JsonNode identifier : identifiers) {
                    String system = identifier.path("type").path("coding").path(0).path("code").asText(null);
                    String value = identifier.path("value").asText(null);
                    if (system != null && value != null) {
                        switch (system) {
                            case "national-id" -> member.setNationalId(value);
                            case "sha-number" -> member.setShaNumber(value);
                            case "household-number" -> member.setHouseholdNumber(value);
                            case "wallet-id" -> member.setWalletId(value);
                            case "birth-certificate" -> member.setBirthCertificate(value);
                        }
                    }
                }
            }

            // Extract name
            JsonNode nameNode = entryNode.path("name").path(0);
            if (!nameNode.isMissingNode()) {
                String patientName = nameNode.path("text").asText(null);
                member.setName(patientName != null ? patientName.trim() : null);
                log.info("Patient Name: {}", member.getName());
            }

            // Extract telecom details
            JsonNode telecoms = entryNode.path("telecom");
            if (telecoms.isArray()) {
                for (JsonNode telecom : telecoms) {
                    String system = telecom.path("system").asText(null);
                    String value = telecom.path("value").asText(null);
                    if ("phone".equals(system)) {
                        member.setPhoneNumber(value);
                    } else if ("email".equals(system)) {
                        member.setEmail(value);
                    }
                }
            }

            // Extract gender
            member.setGender(entryNode.path("gender").asText(null));

            // Extract birthdate
            String birthDateString = entryNode.path("birthDate").asText(null);
            if (birthDateString != null) {
                member.setBirthDate(parseAndValidateDate(birthDateString));
                log.info("Patient Birth Date: {}", member.getBirthDate());
            }

            // Extract address details
            JsonNode addressArray = entryNode.path("address");
            if (addressArray.isArray() && !addressArray.isEmpty()) {
                JsonNode addressNode = addressArray.get(0);
                member.setCity(addressNode.path("city").asText(null));
                member.setCountry(addressNode.path("country").asText(null));

                StringBuilder addressLine = new StringBuilder();
                JsonNode extensions = addressNode.path("extension");
                if (extensions.isArray()) {
                    for (JsonNode extension : extensions) {
                        String url = extension.path("url").asText(null);
                        String valueString = extension.path("valueString").asText(null);
                        if (url != null && valueString != null) {
                            if (url.contains("patients-county")) {
                                member.setCounty(valueString);
                                addressLine.append(valueString).append(",");
                            } else if (url.contains("patients-sub-county")) {
                                member.setDistrict(valueString);
                                addressLine.append(valueString).append(",");
                            } else if (url.contains("patients-ward")) {
                                member.setCity(valueString);
                                addressLine.append(valueString);
                            }
                        }
                    }
                }
                member.setAddressLine(addressLine.toString());
            }

            // Extract marital status
            JsonNode maritalStatusNode = entryNode.path("maritalStatus").path("coding").path(0).path("code");
            if (!maritalStatusNode.isMissingNode()) {
                member.setMaritalStatus(maritalStatusNode.asText(null));
            }
            log.info("fromJsonBundle to Member Object : {}", member.getCrId());
            return member;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing JSON", e);
        }
    }

    /**
     * Parses and validates a date string in "yyyy-MM-dd" format.
     *
     * @param dateStr the date string to parse
     * @return the parsed Date object
     * @throws DataValidationException if the date is invalid or out of range
     */
    private static Date parseAndValidateDate(String dateStr) throws DataValidationException {
        try {
            if (dateStr == null || dateStr.isBlank()) {
                log.error("DOB is missing");
                throw new DataValidationException("DOB is missing");
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false);
            Date dob = sdf.parse(dateStr.trim());

            long now = System.currentTimeMillis();
            long min = now - 200L * 365 * 24 * 60 * 60 * 1000;

            Calendar c = Calendar.getInstance(); c.setTime(dob);
            if (dob.getTime() > now || dob.getTime() < min || c.get(Calendar.YEAR) == 1) {
                log.error("Invalid DOB");
                throw new DataValidationException("Invalid DOB");
            }
            return dob;
        } catch (ParseException e) {
            log.error("Invalid date format: {}", dateStr, e);
            throw new IllegalArgumentException("Invalid format (yyyy-MM-dd)", e);
        }
    }

    //For BMS
    @Column(name = "member_number")
    private String memberNumber;

    @Column(name = "principal_member_number")
    private String principalMemberNumber;

    @Column(name = "job_group_id", insertable = false, updatable = false)
    private Long jobGroupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_group_id", referencedColumnName = "id")
    private JobGroup jobGroup;

    @Column(name = "member_relation_id", insertable = false, updatable = false)
    private Long memberRelationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_relation_id", referencedColumnName = "id")
    private MemberRelation memberRelation;

  

}
