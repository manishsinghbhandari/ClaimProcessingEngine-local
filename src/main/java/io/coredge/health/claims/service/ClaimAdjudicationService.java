package io.coredge.health.claims.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.google.gson.Gson;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClaimAdjudicationService {

    @Autowired
    private IGenericClient fhirClient;

    public String getAdjudicationFromClaimResponse(String claimResponseID){
        ClaimResponse claimResponse = fhirClient
                .read()
                .resource(ClaimResponse.class)
                .withId(claimResponseID)
                .execute();
        List<ClaimResponse.ItemComponent> adjudication = claimResponse.getItem();
        return new Gson().toJson(adjudication);
    }
}
