package io.coredge.health.claims.rest;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import io.camunda.zeebe.client.ZeebeClient;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static io.coredge.health.claims.constants.Constants.EndPoints.CLAIMS_INTERNAL_API_PATH;

@RestController
@RequestMapping(CLAIMS_INTERNAL_API_PATH)
@Slf4j
public class ClaimInternalController {

    @Autowired
    private IGenericClient fhirClient;

    @GetMapping("/")
    public List<Bundle> getFilteredBundles(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String identifier,
            @RequestParam(required = false) String date) {
        IQuery<IBaseBundle> query = fhirClient.search().forResource(Bundle.class)
                .where(Bundle.TYPE.exactly().code("message")) // Filter by Bundle type "message"
                .where(Bundle.MESSAGE.hasChainedProperty(MessageHeader.EVENT.exactly().code("claim-request"))); // Filter by MessageHeader.eventCoding code
        // Execute the query
        Bundle bundle = query.returnBundle(Bundle.class).execute();
        return bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)
                .filter(resource -> resource instanceof Bundle)
                .map(resource -> (Bundle) resource).toList();
    }
}
