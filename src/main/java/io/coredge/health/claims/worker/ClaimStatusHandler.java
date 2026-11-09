package io.coredge.health.claims.worker;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.coredge.health.claims.exception.app.ClaimUnProcessableException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ClaimStatusHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ClaimStatusHandler.class);

  @JobWorker(type = "handleClaimStatusUpdate", autoComplete = false)
  public void handle(JobClient client, ActivatedJob job) {
    LOG.info("Handling credit card payment for process instance {}", job.getProcessInstanceKey());
    Map<String, Object> variables = job.getVariablesAsMap();
    try {
      client.newCompleteCommand(job).send();
    } catch (ClaimUnProcessableException e) {
      client
          .newThrowErrorCommand(job)
          .errorCode("claimStatusUpdateError")
          .errorMessage(e.getLocalizedMessage())
          .variables(Map.of("errorMessage", e.getLocalizedMessage()))
          .send();
    }
  }
}
