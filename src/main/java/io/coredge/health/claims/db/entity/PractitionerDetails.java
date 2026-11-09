package io.coredge.health.claims.db.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;


@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Cacheable
@Entity
@Slf4j
@Table(name = "m_practitioner_details")
public class PractitionerDetails extends StatusBase{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, length = 50)
    private Long id;

    @Column(name = "reg_no", nullable = false, length = 50)
    private String regNo;

    @Column(name = "full_names", nullable = false, length = 255)
    private String fullNames;

    @Column(name = "pu_id", nullable = false, length = 50 , unique = true)
    private String puId;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "mobile_no", length = 50)
    private String mobileNo;

    @Column(name = "id_number", length = 50)
    private String idNumber;

    @Column(name = "id_type", length = 50)
    private String idType;

    @Column(name = "cadre", length = 100)
    private String cadre;

    @Column(name = "qualifications", columnDefinition = "TEXT")
    private String qualifications;

    @Column(name = "licence_number", length = 50)
    private String licenceNumber;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "validity")
    private LocalDate validity;

    @Column(name = "licence_status")
    private String licenceStatus;

    @Column(name = "specialty", length = 100)
    private String specialty;

    @Column(name = "sub_specialty", length = 100)
    private String subSpecialty;

    @Column(name = "licence_type", length = 50)
    private String licenceType;

    @Column(name = "postal_address", columnDefinition = "TEXT")
    private String postalAddress;

    @Column(name = "discipline_name", length = 100)
    private String disciplineName;


    public static PractitionerDetails fromJsonBundle(String json) {
        try {
            log.info("Processing practitioner fromJsonBundle to PractitionerDetails Object");
            JsonNode entryNode = new ObjectMapper().readTree(json);
            PractitionerDetails details = new PractitionerDetails();

            for (JsonNode identifier : entryNode.path("identifier")) {
                String code = identifier.path("type").path("coding").get(0).path("code").asText(null);
                String value = identifier.path("value").asText(null);
                switch (code) {
                    case "board-registration-number" -> details.setRegNo(value);
                    case "national-id" -> {
                        details.setIdNumber(value);
                        details.setIdType(identifier.path("type").path("coding").get(0).path("display").asText(null));
                    }
                    case "license-number" -> details.setLicenceNumber(value);
                }
            }

            for (JsonNode extension : entryNode.path("extension")) {
                String url = extension.path("url").asText(null);
                String display = extension.path("valueCodeableConcept").path("coding").get(0).path("display").asText(null);
                if ("/StructureDefinition/speciality".contains(url)) details.setSpecialty(display);
                else if ("/StructureDefinition/sub-speciality".contains(url)) details.setSubSpecialty(display);
            }

            details.setFullNames(entryNode.path("name").get(0).path("text").asText(null));

            for (JsonNode telecom : entryNode.path("telecom")) {
                String system = telecom.path("system").asText(null);
                String value = telecom.path("value").asText(null);
                if ("email".equals(system)) details.setEmail(value);
                else if ("phone".equals(system)) details.setMobileNo(value);
            }

            details.setGender(entryNode.path("gender").asText(null));
            details.setPuId(entryNode.path("id").asText(null));

            for (JsonNode qualification : entryNode.path("qualification")) {
                details.setLicenceType(qualification.path("extension").get(0).path("valueString").asText(null));
                details.setLicenceStatus(qualification.path("extension").get(1).path("valueString").asText(null));
            }
            return details;
        } catch (Exception e) {
            log.error("Error processing JSON to PractitionerDetails", e);
            return null;
        }
    }

    public static PractitionerDetails fromPractitioner(Practitioner practitioner){
        try {
            log.info("Processing Practitioner to PractitionerDetails Object");
            PractitionerDetails details = new PractitionerDetails();

            for (Identifier identifier : practitioner.getIdentifier()) {
                String code = getIdentifierKey(identifier);
                String value = identifier.getValue();
                switch (code) {
                    case "board-registration-number", "PractitionerRegistrationNumber" -> details.setRegNo(value);
                    case "national-id" -> {
                        details.setIdNumber(value);
                        details.setIdType(identifier.getType().getCodingFirstRep().getDisplay());
                    }
                    case "National_ID" ->{
                        details.setIdNumber(value);
                        details.setIdType("National ID");
                    }
                    case "license-number" -> details.setLicenceNumber(value);
                }
            }

            for (Extension extension : practitioner.getExtension()) {
                String url = extension.getUrl();
                CodeableConcept specialization = (CodeableConcept) extension.getValue();
                String display = specialization.getText();
                if ("/StructureDefinition/speciality".contains(url)) details.setSpecialty(display);
                else if ("/StructureDefinition/sub-speciality".contains(url)) details.setSubSpecialty(display);
            }

            details.setFullNames(practitioner.getNameFirstRep().getText());

            for (ContactPoint telecom : practitioner.getTelecom()) {
                String value = telecom.getValue();
                if(Objects.isNull(telecom.getSystem())){
                    details.setMobileNo(telecom.getValue());
                }
                else {
                    String system = telecom.getSystem().getSystem();
                    if ("email".equals(system)) details.setEmail(value);
                    else if ("phone".equals(system)) details.setMobileNo(value);
                }
            }

            String gender = Optional.ofNullable(practitioner.getGender()).map(Enumerations.AdministrativeGender::getDisplay).orElse(null);
            details.setGender(gender);
            details.setPuId(practitioner.getIdPart());

            for (Practitioner.PractitionerQualificationComponent qualification : practitioner.getQualification()) {

                if (qualification.hasExtension()) {
                    qualification.getExtension().forEach(ext -> {
                        if (ext.getUrl().contains("Specialty")) {
                            details.setLicenceType(ext.getValue().primitiveValue());
                        }
                        if (ext.getUrl().contains("LicenseBody")) {
                            details.setLicenceStatus(ext.getValue().primitiveValue());
                        }
                    });
                }
            }

            return details;
        } catch (Exception e) {
            log.error("Error while processing the PractitionerDetails {},", e.getMessage());
             throw new RuntimeException(e.getMessage());
        }
    }

    private static String getIdentifierKey(Identifier identifier) {
        if(identifier == null) {
            return null;
        }
        if(identifier.hasType() && identifier.getType().hasCoding()) {
            return identifier.getType().getCodingFirstRep().getCode();
        }
        else {
            return getLastSegment(identifier.getSystem());
        }
    }

    private static String getLastSegment(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        int idx = input.lastIndexOf('/');
        if (idx == -1 || idx == input.length() - 1) {
            return "";
        }
        return input.substring(idx + 1);
    }
}
