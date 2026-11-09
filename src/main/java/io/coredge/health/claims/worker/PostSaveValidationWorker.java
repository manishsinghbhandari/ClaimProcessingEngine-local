
package io.coredge.health.claims.worker;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.coredge.health.claims.constants.camunda.ClaimHandlerConstants;
import io.coredge.health.claims.constants.intervention.InterventionConstants;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.service.PostSaveValidationService;
import io.coredge.health.claims.service.ProcessNotesService;
import io.coredge.health.claims.service.intervention.InterventionValidatorHelper;
import io.coredge.health.claims.utils.AsyncUtils;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.coredge.health.claims.utils.ClaimsValidator;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.coredge.health.claims.constants.Constants.HandlerMethod.POST_SAVE_VALIDATION_HANDLER;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;
import static io.coredge.health.claims.constants.intervention.InterventionConstants.MINIMUM_LOS;

/**
 * Camunda Job Worker that runs post-save validations on claims and pre-authorizations.
 * <p>
 * If validations fail, sets {@code isPostSaveValidationFailed=true} and attaches validation
 * errors as process notes on the ClaimResponse. The BPMN flow then routes to the existing
 * Return To Sender handler ({@code handleClaimReturnedEvent} or {@code handlePreAuthReturnedEvent}),
 * which updates the status to SENT_BACK and notifies the provider.
 */
@Slf4j
@Component
public class PostSaveValidationWorker {

    @Autowired
    private PostSaveValidationService validationService;

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private InsuranceClaimsRepository claimsRepository;

    @Autowired
    private ProcessNotesService processNotesService;

    @Autowired
    private InterventionValidatorHelper interventionValidatorHelper;

    @Autowired
    private ClaimsValidator claimsValidator;

    /**
     * Executes post-save validations on a claim/preauth within the BPMN workflow.
     * <p>
     * On failure: sets {@code isPostSaveValidationFailed=true} and adds validation errors
     * as process notes on the ClaimResponse. The BPMN flow will then route to the existing
     * Return To Sender handler which handles status update (SENT_BACK) and provider notification.
     * <p>
     * On success: sets {@code isPostSaveValidationFailed=false} and continues the normal flow.
     */
    @JobWorker(type = POST_SAVE_VALIDATION_HANDLER)
    public Map<String, Object> postSaveValidation(ActivatedJob job) {

        return AsyncUtils.withCamundaContext(job, () -> {

            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            Map<String, Object> variables = job.getVariablesAsMap();

            log.info("Running post-save validations for claimId: {}", claimId);
            ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId).execute();

            try {
                // Load resources
                Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();

                InsuranceClaim iClaim = claimsRepository.findByExternalId(claimId).orElseThrow(()
                        -> new DataNotFoundException("Claim not found")).getFirst();

                Provider provider = iClaim.getProvider();

            /*
             -------------------------------------
             1. Overnight Stay Validation
             -------------------------------------
             */

                List<String> overnightErrors = validateOvernightStay(claim);

                if (!overnightErrors.isEmpty()) {

                    String errorMessage = String.join("; ", overnightErrors);

                    log.warn("Overnight stay validation FAILED for claimId: {} -> {}", claimId, errorMessage);

                    variables.put(ClaimHandlerConstants.IS_OVERNIGHT_STAY_FAILED, ClaimHandlerConstants.TRUE);
                    variables.put(ClaimHandlerConstants.IS_POST_SAVE_VALIDATION_FAILED, ClaimHandlerConstants.FALSE);
                    variables.put(ClaimHandlerConstants.VALIDATION_ERRORS, errorMessage);

                    claimResponse.addProcessNote().setText(ClaimHandlerConstants.VALIDATION_FAILED_PREFIX + errorMessage);

                    fhirClient.update().resource(claimResponse).execute();

                    processNotesService.saveProcessNotes(claimId, ExtensionStateCodeEnum.REJECTED,
                            ClaimHandlerConstants.VALIDATION_FAILED_PREFIX + errorMessage);

                    return variables;
                }

                variables.put(ClaimHandlerConstants.IS_OVERNIGHT_STAY_FAILED, ClaimHandlerConstants.FALSE);

            /*
             -------------------------------------
             2. Other Post Save Validations
             -------------------------------------
             */

                // Fetch the original input bundle from FHIR using bundleId stored on the InsuranceClaim entity.
                // The bundle is needed by validateRelatedPreAuth to resolve patient/provider references.
                String bundleId = iClaim.getBundleId();
                Bundle bundle = null;
                if (bundleId != null && !bundleId.isBlank()) {
                    try {
                        bundle = fhirClient.read().resource(Bundle.class).withId(bundleId).execute();
                    } catch (Exception e) {
                        log.warn("Could not fetch bundle {} for post-save validation — continuing without bundle: {}", bundleId, e.getMessage());
                    }
                } else {
                    log.warn("bundleId not found in Camunda variables for claimId: {}", claimId);
                }

                List<String> errors = validationService.validate(claim, bundle, null, provider);

                if (errors.isEmpty()) {

                    log.info("Post-save validations passed for claimId: {}", claimId);
                    variables.put(ClaimHandlerConstants.IS_POST_SAVE_VALIDATION_FAILED, ClaimHandlerConstants.FALSE);

                } else {

                    String errorMessage = String.join("; ", errors);

                    log.warn("Post-save validations failed for claimId: {} -> {}", claimId, errorMessage);

                    variables.put(ClaimHandlerConstants.IS_POST_SAVE_VALIDATION_FAILED, ClaimHandlerConstants.TRUE);
                    variables.put(ClaimHandlerConstants.VALIDATION_ERRORS, errorMessage);

                    claimResponse.addProcessNote().setText(ClaimHandlerConstants.VALIDATION_FAILED_PREFIX + errorMessage);
                    fhirClient.update().resource(claimResponse).execute();

                    processNotesService.saveProcessNotes(claimId, ExtensionStateCodeEnum.SENT_BACK,
                            ClaimHandlerConstants.VALIDATION_FAILED_PREFIX + errorMessage);
                }

            } catch (Exception e) {

                log.error("Error during post-save validation for claimId: {} error: {} class: {}", claimId, e.getMessage(), e.getClass().getName());
                e.printStackTrace();

                variables.put(ClaimHandlerConstants.IS_SAVE_VALIDATION, ClaimHandlerConstants.TRUE);
                variables.put(ClaimHandlerConstants.IS_POST_SAVE_VALIDATION_FAILED, ClaimHandlerConstants.TRUE);
                variables.put(ClaimHandlerConstants.VALIDATION_ERRORS,
                        ClaimHandlerConstants.INTERNAL_VALIDATION_ERROR_PREFIX + e.getMessage());
            }

            return variables;
        });
    }

    public List<String> validateOvernightStay(Claim claim) {

        List<String> errors = new ArrayList<>();
        List<String> codes = interventionValidatorHelper.extractInterventionCodes(claim);
        int los = interventionValidatorHelper.calculateLos(claim);
        if (los < MINIMUM_LOS) {
            codes.stream()
                    .filter(InterventionConstants.OVERNIGHT_STAY_SHA_CODES::contains)
                    .forEach(code ->
                            errors.add("Overnight stay violation for intervention " + code +
                                    ". Admission and discharge dates must not be the same day."));
        }

        return errors;
    }

}
