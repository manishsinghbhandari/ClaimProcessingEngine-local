package io.coredge.health.claims.helpers;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.enums.HieSource;
import io.coredge.health.claims.enums.IdentifierType;
import io.coredge.health.claims.exception.app.HIEAuthenticationException;
import io.coredge.health.claims.exception.app.HIEParseException;
import io.coredge.health.claims.exception.app.HIEServiceNotAvailableException;
import io.coredge.health.claims.exception.app.ProviderNotFoundException;
import io.coredge.health.claims.service.HealthExchangeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HieHelper {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HealthExchangeService hieService;
    private final ApplicationProperties.Hie hieProperties;

    public HieHelper(HealthExchangeService hieService, ApplicationProperties.Hie hieProperties) {
        this.hieService = hieService;
        this.hieProperties = hieProperties;
    }

    public String getPatientFromHieViaNationalID(String patientId) {
        return getPatientFromHie(patientId, "National ID");
    }

    public String getPatientFromHieViaShaNumber(String patientId) {
        return getPatientFromHie(patientId, "SHA Number");
    }

    public String getPractitionerFromHieViaRegNo(String practitionerRegNo) {
        return getPractitionerFromHie("registration_number", practitionerRegNo);
    }

    public String getPatientFromHie(String patientId, String identifier) {
        try {
            String patientJson = hieService.fetchPatient(identifier, patientId);
            JsonNode rootNode = objectMapper.readTree(patientJson).path("entry").path(0).path("resource");
            if (!rootNode.isMissingNode() && !rootNode.isNull()) {
                return objectMapper.writeValueAsString(rootNode);
            }
            return null;
        } catch (Exception e) {
            log.error("Exception occurred while fetching data from HIE. PatientId: {}. Error: {}", patientId, e.getMessage(), e);
            throw new RuntimeException(String.format("Unable to retrieve beneficiary data for PatientId [%s] from the HIE system. " +
                    "Please check the system logs for more details.", patientId), e);
        }
    }

    public String getPractitionerFromHie(String identifier, String practitionerRegNo) {
        try {
            return hieService.fetchPractitioner(identifier, practitionerRegNo);
        } catch (Exception e) {
            log.error("Exception occurred while fetching data from HIE. practitionerRegNo: {}. Error: {}", practitionerRegNo, e.getMessage(), e);
            throw new RuntimeException(String.format("Unable to retrieve beneficiary data for practitionerRegNo [%s] from the HIE system. " +
                    "Please check the system logs for more details.", practitionerRegNo), e);
        }
    }

    public String getProviderFromHie(String searchValue, IdentifierType identifierType) {
        try {
            return hieProperties.getProviderSource().equalsIgnoreCase(HieSource.DHA.name())
                    ? hieService.fetchProviderFromDHA(searchValue, identifierType)
                    : hieService.fetchProviderFromSafariCom(searchValue, identifierType);
        } catch (ProviderNotFoundException | HIEServiceNotAvailableException |
                 HIEAuthenticationException | HIEParseException expectedException) {
            // Rethrow expected exceptions directly
            throw expectedException;
        } catch (Exception e) {
            // Log and wrap unknown errors
            log.error("❌ Unexpected error while fetching provider data from HIE. ProviderId: {}. Message: {}",
                    searchValue, e.getMessage(), e);

            throw new RuntimeException(String.format(
                    "Unexpected error retrieving provider data for ProviderId [%s] from the HIE system. " +
                            "Please check the logs for more info.", searchValue), e);
        }
    }

}
