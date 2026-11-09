package io.coredge.health.claims.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ClaimExtensionService {
    @Value("${TERMINOLOGY_SERVER_BASE_URL:https://mis.apeiro-digital.com/fhir}")
    private String fhirServerBaseUrl;

    @Autowired
    private IGenericClient fhirClient;

    public void updateState(DomainResource resource, ExtensionStateCodeEnum state) {
        String extnUrl = fhirServerBaseUrl + "/StructureDefinition/claim-state-extension";

        // Remove any existing extension with the same URL
        resource.getExtension().removeIf(ext -> extnUrl.equals(ext.getUrl()));
        Extension stateExtension = resource.hasExtension(extnUrl) ? resource.getExtensionByUrl(extnUrl) : new Extension(extnUrl);
        Coding stateCoding = new Coding()
                .setSystem(fhirServerBaseUrl + "/CodeSystem/claim-state")
                .setCode(state.getStateCode())
                .setDisplay(state.getDisplayName());

        stateExtension.setValue(new CodeableConcept().addCoding(stateCoding));

        if (!resource.hasExtension(extnUrl)) {
            resource.addExtension(stateExtension);
        }
    }

    public boolean verifyState(ClaimResponse resource, ExtensionStateCodeEnum state) {
        String extnUrl = fhirServerBaseUrl + "/StructureDefinition/claim-state-extension";
        Extension stateExtension;
        if (resource.hasExtension(extnUrl)) {
            stateExtension = resource.getExtensionByUrl(extnUrl);
            if (stateExtension.hasValue()) {
                Type type = stateExtension.getValue();
                CodeableConcept codeableConcept = ((CodeableConcept) type);
                String value = codeableConcept.getCoding().getFirst().getCode();
                if (value != null && value.equalsIgnoreCase(state.getStateCode())) {
                    return true;
                }
            }
        }
        return false;
    }


}
