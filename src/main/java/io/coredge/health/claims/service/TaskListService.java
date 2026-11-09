package io.coredge.health.claims.service;

import io.camunda.zeebe.client.ZeebeClient;
import io.coredge.health.claims.db.entity.WorkFlow;
import io.coredge.health.claims.db.repositories.WorkFlowRepository;

import java.util.*;

import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataValidationException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TaskListService {

    @Autowired
    private WorkFlowRepository repository;

    @Autowired
    private ZeebeClient zeebe;

    public static final String VARIABLE_KEY_CLAIM_ID = "claimId";
    public static final String VARIABLE_KEY_CLAIM_RESPONSE_ID = "claimResponseId";

    public List<WorkFlow> findAll() {
        return repository.findAll();
    }

    public Optional<WorkFlow> findById(Long id) {
        return repository.findById(id);
    }

    public WorkFlow save(WorkFlow workFlow) {
        return repository.save(workFlow);
    }

    public void completeTask(String messageName, Map<String, Object> body) {
        try {
            if (body != null && !body.isEmpty() && body.get(VARIABLE_KEY_CLAIM_ID) != null) {
                String claimId = (String) body.get(VARIABLE_KEY_CLAIM_ID);
                //completeJob(response.get().getJobKey(), body);
                completeJob(messageName , claimId , body);
            } else {
                log.warn("Invalid body json during completeTask()");
                throw new Exception("Invalid Body json");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    private void completeJob(Long jobKey, Map<String, Object> variableMap) {
//        zeebe.newCompleteCommand(jobKey).variables(variableMap).send().join();
//    }

    private void completeJob(String messageName, String claimId, Map<String, Object> variables) {
        zeebe.newPublishMessageCommand()
                .messageName(messageName)
                .correlationKey(claimId)
                .variables(variables)
                .send()
                .join();
    }

    public Boolean isExistByJobKey(Long jobKey) {
        Optional<Boolean> isExistByClaimID = repository.existsByJobKey(jobKey);
        return isExistByClaimID.orElse(false);
    }

    /**
     * This method will save new workflow data into the database.
     *
     * @param processInstanceKey
     * @param claimId
     * @param claimResponseId
     */
    @Transactional
    public void saveNewWfData(long processInstanceKey, String claimId, String claimResponseId) {
        log.info("Saving new workflow data with claimId: {} and ProcessInstanceKey: {}", claimId, processInstanceKey);
        try {
            WorkFlow wf = WorkFlow.builder().claimId(claimId).claimResponseId(claimResponseId).processInstanceKey(processInstanceKey).active(true).build();
            repository.save(wf);
        } catch (Exception e) {
            log.error("Error while saving new workflow data for claimId: {} ProcessInstanceKey: {} {}", claimId, processInstanceKey, e.getMessage());
            throw new BadRequestException(String.format("Error while saving new workflow data for claimId: %s ProcessInstanceKey: %s %s", claimId, processInstanceKey, e.getMessage()));
        }
    }

    /**
     * This method will delete all duplicate process workflows for a given claimId,
     * keeping only the oldest one active.
     *
     * @param claimId
     */
    @Transactional
    public void deleteDuplicateProcessWorkflows(String claimId, long currentProcessInstanceKey) {

        var workflowsOpt = repository.findAllByClaimIdAndActiveIsTrue(claimId);
        if (workflowsOpt.isPresent() && !workflowsOpt.get().isEmpty()) {
            var workflows = workflowsOpt.get();
            // Fix Self-Cancellation of workflow
            List<WorkFlow> workflowsToDelete = workflows.stream()
                    .filter(wf -> wf.getProcessInstanceKey() != currentProcessInstanceKey)
                    .toList();
            for (WorkFlow wf : workflowsToDelete) {
                Long processInstanceKey = wf.getProcessInstanceKey();
                log.info("Cancelling and deleting workflow processInstance: {} for claimId: {}", processInstanceKey, claimId);
                zeebe.newCancelInstanceCommand(processInstanceKey).send().join();
                wf.setActive(false);
                repository.save(wf);
            }
            log.info("All active workflows except the oldest deleted for claimId: {}", claimId);
        }
    }

    /**
     * This method will update the workflow data in the database.
     *
     * @param claimId
     * @param jobKey
     * @param processInstanceKey
     */
    @Transactional
    public void updateWfData(String claimId, long jobKey, long processInstanceKey) {
        log.info("Saving new workflow data with claimId: {} and JobKey: {}", claimId, jobKey);
        try {
            Optional<WorkFlow>response = repository.findByClaimIdAndProcessInstanceKey(claimId, processInstanceKey);
            if (response.isPresent()) {
                WorkFlow wf = response.get();
                wf.setJobKey(jobKey);
                repository.save(wf);
            }
        } catch (Exception e) {
            log.error("Error while updating workflow data for claimId: {} JobKey: {} {}", claimId, jobKey, e.getMessage());
        }
    }

}
