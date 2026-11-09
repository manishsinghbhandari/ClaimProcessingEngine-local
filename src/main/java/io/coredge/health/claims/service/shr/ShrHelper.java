package io.coredge.health.claims.service.shr;

import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.exception.shr.ShrDataNotFoundException;
import io.coredge.health.claims.exception.shr.ShrFetchException;
import io.coredge.health.claims.exception.shr.ShrServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class ShrHelper {

    private final ShrService shrService;
    private final IParser fhirParser;
    private final FhirValidator validator;


    private static final String IPS = "ips";

    public ShrHelper(ShrService shrService, IParser fhirParser, FhirValidator validator) {
        this.shrService = shrService;
        this.fhirParser = fhirParser;
        this.validator = validator;
    }

    public String extractCrIdFromPatient(Patient patient) {
        if (!patient.hasIdElement()) {
            return null;
        }
        return patient.getIdElement().getIdPart();
    }

    public String extractIdentifierContainingIps(Claim claim) {
        return Optional.ofNullable(claim)
                .flatMap(p -> p.getIdentifier().stream()
                        .filter(id -> id.hasSystem() && id.getSystem().toLowerCase().contains(IPS))
                        .map(Identifier::getValue)
                        .findFirst())
                .orElse(StringUtils.EMPTY);
    }

    public void extractIpsInformation(Bundle responseBundle, Patient patient, Claim claim) {
        String ipsValue = extractIdentifierContainingIps(claim);
        if (ipsValue.isBlank() || !IPS.equals(ipsValue)) {
            return;
        }
        log.debug("IPS found in bundle: {}", ipsValue);
        String patientId = extractCrIdFromPatient(patient);
        ResponseEntity<String> ipsResponse = fetchShrSummary(patientId, claim);
        Bundle ipsBundle = parseAndValidateBundle(ipsResponse.getBody());
        responseBundle.getEntry().addAll(ipsBundle.getEntry());
    }

    private ResponseEntity<String> fetchShrSummary(String patientId, Claim claim) {
        log.debug("Fetching the IPS information for this claim: {}", claim.getIdElement().getIdPart());
        ResponseEntity<String> response = Objects.requireNonNull(shrService.getShrSummary(patientId).block());

        if (response.getStatusCode().is5xxServerError()) {
            throw new ShrServiceUnavailableException("SHR service is currently unavailable.");
        } else if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new ShrDataNotFoundException(shrService.extractDiagnostics(response.getBody()));
        } else if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
            throw new ShrFetchException("Bad request"); // generic fallback, good
        }
        return response;
    }

    public String extractDiagnostics(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            return root.path("issue").get(0).path("diagnostics").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    public Bundle parseAndValidateBundle(String bundleJson) {
        Bundle bundle = fhirParser.parseResource(Bundle.class, bundleJson);
        ValidationResult result = validator.validateWithResult(bundle);
        if (!result.isSuccessful()) {
            log.warn("Validation errors in bundle: {}", result.getMessages().toString());
        }
        return bundle;
    }
}