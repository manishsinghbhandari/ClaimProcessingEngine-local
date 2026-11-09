package io.coredge.health.claims.models.registry;

import lombok.Data;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Coding;
import java.util.Date;
import java.util.Optional;

@Data
public class HealthWorker {

    private Long id;

    private String fhirPractitionerId;

    private String nationalId;

    private String boardRegistrationNumber;

    private boolean active;

    private String name;

    private String gender;

    private Date birthDate;

    private String professionalCadre;

    private String licenseNumber;

    private String licensingBody;

    private String qualificationType;

    // Getters and Setters

    // Method to map FHIR Practitioner to PractitionerEntity
    public static HealthWorker fromFhirPractitioner(Practitioner fhirPractitioner) {
        HealthWorker entity = new HealthWorker();

        // Set the FHIR Practitioner ID
        entity.setFhirPractitionerId(fhirPractitioner.getIdElement().getIdPart());

        // Extract identifiers based on system and code
        for (Identifier identifier : fhirPractitioner.getIdentifier()) {
            String system = identifier.getType().getCodingFirstRep().getSystem();
            String code = identifier.getType().getCodingFirstRep().getCode();
            String value = identifier.getValue();

            if ("http://hwr-kenyahie.health".equals(system) && "national-id".equals(code)) {
                entity.setNationalId(value);
            } else if ("https://shr.tiberbuapps.com/fhir".equals(system) && "Board Registration Number".equals(identifier.getType().getCodingFirstRep().getDisplay())) {
                entity.setBoardRegistrationNumber(value);
            }
        }

        // Extract active status
        entity.setActive(fhirPractitioner.getActive());

        // Extract name
        Optional<HumanName> nameOptional = fhirPractitioner.getName().stream().findFirst();
        entity.setName(nameOptional.map(HumanName::getText).orElse(null));

        // Extract gender
        entity.setGender(fhirPractitioner.getGender() != null ? fhirPractitioner.getGender().toCode() : null);

        // Extract birthDate
        entity.setBirthDate(fhirPractitioner.getBirthDate());

        // Extract professional cadre from extension
        fhirPractitioner.getExtension().stream()
                .filter(ext -> "https://shr.tiberbuapps.com/fhir/StructureDefinition/professional-cadre".equals(ext.getUrl()))
                .findFirst()
                .ifPresent(ext -> {
                    Coding cadre = (Coding) ext.getValue();
                    entity.setProfessionalCadre(cadre.getDisplay());
                });

        // Extract qualifications
        fhirPractitioner.getQualification().stream()
                .findFirst()
                .ifPresent(qualification -> {
                    qualification.getExtension().forEach(ext -> {
                        switch (ext.getUrl()) {
                            case "https://shr.tiberbuapps.com/fhir/StructureDefinition/current-license-number":
                                entity.setLicenseNumber(ext.getValue().primitiveValue());
                                break;
                            case "https://shr.tiberbuapps.com/fhir/StructureDefinition/licensing-body":
                                entity.setLicensingBody(ext.getValue().primitiveValue());
                                break;
                            case "https://shr.tiberbuapps.com/fhir/StructureDefinition/qualification-type":
                                Coding qualificationType = (Coding) ext.getValue();
                                entity.setQualificationType(qualificationType.getDisplay());
                                break;
                        }
                    });
                });

        return entity;
    }
}
