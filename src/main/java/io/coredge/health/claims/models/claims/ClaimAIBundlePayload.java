package io.coredge.health.claims.models.claims;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Bundle;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record ClaimAIBundlePayload(
        @JsonProperty("bundle") Bundle bundle, // Use parsed Bundle instead of raw string
        @JsonProperty("claimId") UUID claimId
) {
    // Constructor to parse claimBundleJson into a Bundle
    public ClaimAIBundlePayload(String claimBundleJson, UUID claimId, IParser fhirParser) {
        this(fhirParser.parseResource(Bundle.class, claimBundleJson), claimId);
    }

    // Optional: Method to access raw claimBundleJson if needed
    public String getRawClaimBundleJson(IParser fhirParser) {
        return fhirParser.encodeResourceToString(bundle);
    }
}