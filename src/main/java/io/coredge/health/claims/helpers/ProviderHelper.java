package io.coredge.health.claims.helpers;

import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.exception.app.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Organization;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class ProviderHelper {

    private final ApplicationProperties.Hie hieProperties;

    public ProviderHelper(ApplicationProperties.Hie hieProperties) {
        this.hieProperties = hieProperties;
    }

    /**
     * Validates mismatches between the provider and HIE organization data.
     * Includes checks for name, level, county, and ownership.
     * @param providerOrg the provider organization
     * @param hieOrg the HIE organization
     */
    public void validateAndMismatch(Organization providerOrg, Organization hieOrg) {
        String providerLevel = getFacilityLevel(providerOrg, "facility-level");
        String hieLevel = getFacilityLevel(hieOrg, "keph-level");
        String providerName = providerOrg.getName();
        String hieName = hieOrg.getName();
        String id = hieOrg.getIdElement().getIdPart();

        validateFacilityLevel(hieLevel, providerLevel, id, providerOrg.getIdElement().getIdPart());
        // validateFacilityName(providerName, hieName, id, providerOrg.getIdElement().getIdPart());
        validateCounty(hieOrg, id);
        validateOwnership(hieOrg, id);
    }

    private void validateFacilityLevel(String hieLevel, String providerLevel, String id, String providerId) {
        if (StringUtils.isEmpty(hieLevel)) {
            log.warn("Facility Level missing in HIE Organization with ID: {}", id);
            throw new FacilityLevelNotFoundException("HIE : " + id);
        }

        if (!hieLevel.equalsIgnoreCase(providerLevel)) {
            log.warn("Mismatch in Facility Level between HIE and Provider. HIE Level: {}, Provider Level: {}, Facility ID: {}", hieLevel, providerLevel, id);
            throw new FacilityLevelMismatchException(providerId, hieLevel, providerLevel);
        }
    }

    public String validateFacilityLevel(String hieLevel, String providerLevel, String providerId) {
        if (StringUtils.isBlank(hieLevel)) {
            return String.format("Facility Level missing in HIE Organization with Provider Id : %s", providerId);
        }

        if (!hieLevel.equalsIgnoreCase(providerLevel)) {
            return String.format("Mismatch in Facility Level between HIE and Provider. HIE Level: %s, Provider Level: %s, Facility ID: %s", hieLevel, providerLevel,providerId);
        }
        return  null;
    }

    private void validateFacilityName(String providerName, String hieName, String id, String providerId) {
        if (hieProperties.isProviderNameMatch() && !StringUtils.equals(providerName, hieName)) {
            log.warn("Mismatch in Facility Name between HIE and Provider. HIE Name: {}, Provider Name: {}, Facility ID: {}", hieName, providerName, id);
            throw new FacilityNameMismatchException(providerId, hieName, providerName);
        }
    }

    private void validateCounty(Organization hieOrg, String id) {
        Optional<Extension> countyExtension = hieOrg.getAddress().stream()
                .flatMap(address -> address.getExtension().stream())
                .filter(ext -> ext.getUrl().contains("county"))
                .findFirst();

        if (countyExtension.isEmpty() || StringUtils.isBlank(countyExtension.get().getValue().toString())) {
            log.warn("County not found in HIE with ID: {}", id);
            throw new FacilityCountyNotFoundException(id);
        }

        String county = countyExtension.get().getValue().toString();
        if (StringUtils.equalsIgnoreCase("Kenya", county)) {
            log.warn("Invalid County: {} found in HIE with ID: {}", county, id);
            throw new FacilityCountyInvalidException(county, id);
        }
    }

    private void validateOwnership(Organization hieOrg, String id) {
        Optional<Extension> ownershipExtensionOpt = hieOrg.getExtension().stream()
                .filter(ext -> ext.getUrl().contains("facility-agent"))
                .findFirst();

        boolean isInvalid = ownershipExtensionOpt.isEmpty()
                || !(ownershipExtensionOpt.get().getValue() instanceof CodeableConcept concept)
                || concept.getCoding().isEmpty()
                || StringUtils.isBlank(concept.getCodingFirstRep().getCode());

        if (isInvalid) {
            log.warn("Facility Ownership coding is missing in HIE with ID: {}", id);
            throw new FacilityOwnerShipNotFoundException(id);
        }
    }

    public String getFacilityLevel(Organization organization, String key) {
        return organization.getExtension().stream()
                .filter(ext -> ext.getUrl() != null && ext.getUrl().contains(key))
                .map(Extension::getValue)
                .filter(CodeableConcept.class::isInstance)
                .map(CodeableConcept.class::cast)
                .map(CodeableConcept::getCodingFirstRep)
                .map(Coding::getCode)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElse(null);
    }

}
