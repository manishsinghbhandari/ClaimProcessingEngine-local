package io.coredge.health.claims.worker;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.camunda.zeebe.client.ZeebeClient;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.mapper.ClaimMapper;
import io.coredge.health.claims.service.CamundaService;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class ClaimProcessService {

  @Autowired private RestTemplate restTemplate;
  @Autowired private ZeebeClient zeebe;

  @Autowired private IGenericClient fhirClient;

  @Autowired private FhirContext fhirContext;

  @Autowired private CamundaService camundaService;

  @Value("${CLAIM_PROCESS_ID:claimProcess}")
  private String bpmnProcessId;

  /** Process a Claim Object on-demand. */
  public void processClaims(Claim claim) {
    String claimCustomDTO =
        ClaimMapper.mapToCustomClaim(claim); // TODO Need to map more filed in the mapper
    // Process each Claim resource here
    // Logic to process
    camundaService.startCamundaProcess(claimCustomDTO , claim.getIdPart());
    //    zeebe.
    //    ProcessInstanceEvent event = zeebe
    //            .newCreateInstanceCommand()
    //            .bpmnProcessId(bpmnProcessId) // The BPMN process ID from the model
    //            .latestVersion()             // Use the latest version of the process
    //            .variables(claimCustomDTO)        // Pass in process variables
    //            .send()
    //            .join();
    //    long processInstanceId = event.getProcessInstanceKey();
    //    log.info("Process Instance Id: " + processInstanceId);

  }

  /** Run every 30 minutes */
  // @Scheduled(fixedRate = Constants.Scheduler.BATCH_FIX_RATE) // 30 min in milliseconds
  public void processClaims() {
    try {
      int batchSize = 10; // Define batch size
      boolean moreResults = true;

      // Fetch the Bundle
      Bundle results = getBundle(batchSize);

      log.info("Bundle results size : {}", results.getEntry().size());
      if (results.getEntry().isEmpty()) {
        log.info("No Claim Data is Found To Process : {}", results.getEntry().size());
        return;
      }
      // Loop through bundle results and create
      while (moreResults) {
        // Process each entry in the current batch
        if (results != null && results.hasEntry()) {
          results
              .getEntry()
              .forEach(
                  entry -> {
                    Claim claim = (Claim) entry.getResource();
                    String claimCustomDTO =
                        ClaimMapper.mapToCustomClaim(
                            claim); // TODO Need to map more filed in the mapper
                    // Process each Claim resource here
                    // Logic to proccess
                    camundaService.startCamundaProcess(claimCustomDTO , claim.getIdPart());
                  });
        }
        // Check if there is a next page
        assert results != null;
        if (results.getLink(Bundle.LINK_NEXT) != null) {
          results = fhirClient.loadPage().next(results).execute(); // Fetch next batch
        } else {
          moreResults = false; // No more pages to load
        }
      }

    } catch (Exception e) {
      log.error("Error occurred while calling FHIR API: {}", e.getMessage());
    }
  }

  public Bundle getBundle(int batchSize) {
    Bundle results = null;
    // Perform a search
    results =
            fhirClient
                    .search()
                    .forResource(Claim.class)
                    // .where(Claim.STATUS.toString().equalsIgnoreCase("ACTIVE"))
                    .count(batchSize) // Fetch data in batches
                    .returnBundle(Bundle.class)
                    .execute();
    return results;
  }
}
