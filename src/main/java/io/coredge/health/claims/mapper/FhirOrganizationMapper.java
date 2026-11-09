package io.coredge.health.claims.mapper;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;

import java.util.*;

@Slf4j
@UtilityClass
public class FhirOrganizationMapper {

    private static final String BED_OCCUPANCY = "bedOccupancy";

    public String getOrganizationBundle(String input) {
        Bundle orgBundle = mapToBundle(input);
        return convertBundleToJson(orgBundle);
    }

    // Method to convert Bundle to Pretty Printed FHIR JSON
    private String convertBundleToJson(Bundle bundle) {
        if (bundle == null) {
            return "{}"; // Return empty JSON if bundle is null
        }
        FhirContext ctx = FhirContext.forR4();
        return ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }
    private Bundle mapToBundle(String jsonResponse) {
        try {
            if (jsonResponse == null || jsonResponse.isEmpty()) {
                log.error("Received null or empty jsonResponse");
                return null;
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootArray = objectMapper.readTree(jsonResponse);
            if (rootArray == null || !rootArray.isArray() || rootArray.isEmpty()) {
                log.error("Invalid JSON format: Expected an array with at least one element");
                return null;
            }

            JsonNode rootNode = rootArray.get(0);
            if (rootNode == null) {
                log.error("Root node is null");
                return null;
            }

            // Create Bundle
            Bundle bundle = new Bundle();
            bundle.setId(UUID.randomUUID().toString());
            bundle.setType(Bundle.BundleType.SEARCHSET);
            bundle.setTotal(1);

            // Generate Organization
            Organization organization = new Organization();
            JsonNode frCodeNode = rootNode.get("frCode");
            if (frCodeNode != null && !frCodeNode.asText().isEmpty()) {
                organization.setId(frCodeNode.asText());
            } else {
                log.error("frCode is missing or empty");
                return null;
            }

            // Add extensions
            if (rootNode.hasNonNull("kephLevel")) {
                String level = rootNode.get("kephLevel").asText();
                organization.addExtension(createExtension(FhirConstants.STRUCTURE_DEFINITION_KEPH_LEVEL, level, level));
            }
            organization.addExtension(createExtension(FhirConstants.STRUCTURE_DEFINITION_REGULATORY_BODY, "kmpdc", "Kenya Medical Practitioners and Dentists Council"));
            organization.addExtension(createStringExtension(FhirConstants.STRUCTURE_DEFINITION_LICENSE_STATUS, "inactive"));

            if (rootNode.hasNonNull("facilityOwnership")) {
                String facilityOwnership = rootNode.get("facilityOwnership").asText();
                organization.addExtension(createExtension(FhirConstants.STRUCTURE_DEFINITION_FACILITY_AGENT, facilityOwnership, facilityOwnership));
            }

            // Add Identifiers
            addIdentifierIfPresent(rootNode, organization, "registrationNumber", "registration-number", "Registration Number");
            addIdentifierIfPresent(rootNode, organization, "frCode", "fr-code", "Facility Registry Code");
            addIdentifierIfPresent(rootNode, organization, "fidCode", "fid", "Facility ID");

            Identifier identifier = getIdentifierIfPresent(rootNode, organization, "fidCode", "license-number", "License Number");

            Period period = new Period();
            if(rootNode.hasNonNull("facilityLicenseStartDate")){
                period.setStart(FhirHelper.convertStringToDate(rootNode.get("facilityLicenseStartDate").asText()));
            }

            if (rootNode.hasNonNull("facilityLicenseEndDate")){
                period.setEnd(FhirHelper.convertStringToDate(rootNode.get("facilityLicenseEndDate").asText()));
            }

            if(Objects.nonNull(identifier)){

                identifier.setPeriod(period);
            }
            else {
                identifier = new Identifier();
                identifier.setPeriod(period);
            }
            organization.addIdentifier(identifier);

            // Active status
            organization.setActive(true);

            // Facility Type
            if (rootNode.hasNonNull("facilityType")) {
                String facilityTypeText = rootNode.get("facilityType").asText();
                CodeableConcept facilityType = new CodeableConcept();
                facilityType.addCoding(new Coding(FhirConstants.STRUCTURE_DEFINITION_FACILITY_TYPE, facilityTypeText.toLowerCase(), facilityTypeText));
                organization.setType(Collections.singletonList(facilityType));
            }

            // Set Name
            if (rootNode.hasNonNull("officialName")) {
                organization.setName(rootNode.get("officialName").asText());
            }

            // Contact Details
            addContactPointIfPresent(rootNode, organization, "facilityEmail", ContactPoint.ContactPointSystem.EMAIL);
            addContactPointIfPresent(rootNode, organization, "facilityAdministratorPhone", ContactPoint.ContactPointSystem.PHONE);
            addContactPointIfPresent(rootNode, organization, "facilityAdministratorName", ContactPoint.ContactPointSystem.OTHER);


            // Set Address
            if (rootNode.hasNonNull("address")) {
                JsonNode addressNode = rootNode.get("address");
                if (addressNode != null && addressNode.isObject()) {
                    Address address = new Address();
                    List<Extension> addressExtensions = new ArrayList<>();

                    addAddressExtensionIfPresent(addressNode, addressExtensions, "county", FhirConstants.STRUCTURE_DEFINITION_COUNTY);
                    addAddressExtensionIfPresent(addressNode, addressExtensions, "countyCode", FhirConstants.STRUCTURE_DEFINITION_COUNTY_CODE);

                    addAddressExtensionIfPresent(addressNode, addressExtensions, "postalAddress", FhirConstants.STRUCTURE_DEFINITION_POSTAL_ADDRESS);
                    addAddressExtensionIfPresent(addressNode, addressExtensions, "physicalLocation", FhirConstants.STRUCTURE_DEFINITION_PHYSICAL_LOCATION);
                    addAddressExtensionIfPresent(addressNode, addressExtensions, "town", FhirConstants.STRUCTURE_DEFINITION_TOWN);

                    address.setExtension(addressExtensions);
                    organization.setAddress(Collections.singletonList(address));
                }
            }

            // Add Bed Occupancy Extension SUPPORT-70
            // Create Extension that represents a 'bedOccupancy' node
            if (rootNode.has(BED_OCCUPANCY) && rootNode.get(BED_OCCUPANCY).isObject()) {
                JsonNode bedOccupancyNode = rootNode.get(BED_OCCUPANCY);
                Extension bedOccupancyHolder = new Extension();
                bedOccupancyHolder.setUrl(FhirConstants.STRUCTURE_DEFINITION_BED_OCCUPANCY);

                List<Extension> bedOccupancyExtensions = new ArrayList<>();

                addBedOccupancyExtension(bedOccupancyNode, "totalBeds", "https://fr-kenyahie/StructureDefinition/total-beds", bedOccupancyExtensions);
                addBedOccupancyExtension(bedOccupancyNode, "normalBeds", "https://fr-kenyahie/StructureDefinition/normal-beds", bedOccupancyExtensions);
                addBedOccupancyExtension(bedOccupancyNode, "icuBeds", "https://fr-kenyahie/StructureDefinition/icu-beds", bedOccupancyExtensions);
                addBedOccupancyExtension(bedOccupancyNode, "hduBeds", "https://fr-kenyahie/StructureDefinition/hdu-beds", bedOccupancyExtensions);
                addBedOccupancyExtension(bedOccupancyNode, "dialysisBeds", "https://fr-kenyahie/StructureDefinition/dialysis-beds", bedOccupancyExtensions);
                addBedOccupancyExtension(bedOccupancyNode, "numberOfCots", "https://fr-kenyahie/StructureDefinition/number-of-cots", bedOccupancyExtensions);

                bedOccupancyHolder.setExtension(bedOccupancyExtensions);
                organization.addExtension(bedOccupancyHolder);
            }

            // Create Bundle Entry
            Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
            entry.setResource(organization);
            bundle.addEntry(entry);

            return bundle;

        } catch (Exception e) {
            log.error("Error in mapping : {}", e.getMessage(), e);
            return null;
        }
    }

    // Utility method to safely add identifiers
    private void addIdentifierIfPresent(JsonNode rootNode, Organization organization, String jsonKey, String system, String display) {
        JsonNode node = rootNode.get(jsonKey);
        if (node != null && !node.asText().isEmpty()) {
            organization.addIdentifier(createIdentifier(system, display, node.asText()));
        }
    }

    // Utility method to safely add identifiers
    private Identifier getIdentifierIfPresent(JsonNode rootNode, Organization organization, String jsonKey, String system, String display) {
        JsonNode node = rootNode.get(jsonKey);
        if (node != null && !node.asText().isEmpty()) {
            return createIdentifier(system, display, node.asText());
        }
        return null;
    }

    // Utility method to safely add contact points
    private void addContactPointIfPresent(JsonNode rootNode, Organization organization, String jsonKey, ContactPoint.ContactPointSystem system) {
        JsonNode node = rootNode.get(jsonKey);
        if (node != null && !node.asText().isEmpty()) {
            ContactPoint contact = new ContactPoint();
            contact.setSystem(system);
            contact.setValue(node.asText());
            organization.setTelecom(Collections.singletonList(contact));
        }
    }

    // Utility method to safely add address extensions
    private void addAddressExtensionIfPresent(JsonNode addressNode, List<Extension> extensions, String jsonKey, String structureDefinition) {
        JsonNode node = addressNode.get(jsonKey);
        if (node != null && !node.asText().isEmpty()) {
            extensions.add(createStringExtension(structureDefinition, node.asText()));
        }
    }

    // Helper method for creating extensions with CodeableConcept
    private static Extension createExtension(String url, String code, String display) {
        Extension extension = new Extension();
        extension.setUrl(url);

        CodeableConcept concept = new CodeableConcept();
        Coding coding = new Coding();
        coding.setSystem(url);
        coding.setCode(code);
        coding.setDisplay(display);
        concept.addCoding(coding);

        extension.setValue(concept);
        return extension;
    }

    // Helper method for creating string extensions
    private static Extension createStringExtension(String url, String value) {
        Extension extension = new Extension();
        extension.setUrl(url);
        extension.setValue(new StringType(value));
        return extension;
    }

    // Helper method for creating Identifiers
    private static Identifier createIdentifier(String code, String display, String value) {
        Identifier identifier = new Identifier();
        identifier.setValue(value);

        CodeableConcept identifierType = new CodeableConcept();
        Coding coding = new Coding();
        coding.setSystem("https://ts.kenya-hie.health/Codesystem/facility-identifier-types");
        coding.setCode(code);
        coding.setDisplay(display);
        identifierType.addCoding(coding);

        identifier.setType(identifierType);
        return identifier;
    }

    private void addBedOccupancyExtension(JsonNode parentNode, String jsonField, String url, List<Extension> extensions) {
        JsonNode valueNode = parentNode.get(jsonField);
        if (valueNode != null && valueNode.isInt()) {
            Extension ext = new Extension();
            ext.setUrl(url);
            ext.setValue(new IntegerType(valueNode.asInt()));
            extensions.add(ext);
        }
    }
}


class FhirConstants {
    public static final String BASE_URL = "https://fr-kenyahie";

    public static final String STRUCTURE_DEFINITION_KEPH_LEVEL = BASE_URL + "/StructureDefinition/keph-level";
    public static final String STRUCTURE_DEFINITION_REGULATORY_BODY = BASE_URL + "/StructureDefinition/regulatory-body";
    public static final String STRUCTURE_DEFINITION_LICENSE_STATUS = BASE_URL + "/StructureDefinition/license-status";
    public static final String STRUCTURE_DEFINITION_FACILITY_AGENT = BASE_URL + "/StructureDefinition/facility-agent";
    public static final String STRUCTURE_DEFINITION_FACILITY_TYPE = BASE_URL + "/StructureDefinition/facility-type";
    public static final String STRUCTURE_DEFINITION_COUNTY = BASE_URL + "/StructureDefinition/county";
    public static final String STRUCTURE_DEFINITION_COUNTY_CODE = BASE_URL + "/StructureDefinition/county-code";
    public static final String STRUCTURE_DEFINITION_POSTAL_ADDRESS = BASE_URL + "/StructureDefinition/postal-address";
    public static final String STRUCTURE_DEFINITION_PHYSICAL_LOCATION = BASE_URL + "/StructureDefinition/physical-location";
    public static final String STRUCTURE_DEFINITION_TOWN = BASE_URL + "/StructureDefinition/town";
    public static final String STRUCTURE_DEFINITION_BED_OCCUPANCY = BASE_URL + "/StructureDefinition/bed-occupancy";


}

