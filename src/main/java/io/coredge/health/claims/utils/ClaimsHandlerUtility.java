package io.coredge.health.claims.utils;

import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import io.coredge.health.claims.client.WebHookClient;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.helpers.ClaimPaymentHelper;
import io.coredge.health.claims.models.ClaimResponseWebhooks;
import io.coredge.health.claims.notification.NotificationService;
import io.coredge.health.claims.service.ClaimExtensionService;
import io.coredge.health.claims.service.InsuranceClaimsService;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.DomainResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.PaymentType.CLAIM;
import static io.coredge.health.claims.constants.Constants.PaymentType.PAYMENT;

@Component
@Slf4j
public class ClaimsHandlerUtility {


    @Value("${enable.webhook.client:true}")
    private boolean enableWebhooksClient;

    @Value("${enable.notification.service:true}")
    private boolean enableNotificationService;

    @Value("${notification.service.provider}")
    private String notificationServiceProvider;

    @Autowired
    private IGenericClient fhirClient;


    @Autowired
    private IParser fhirParser;

    @Autowired
    private ClaimExtensionService extentionService;

    @Autowired
    private InsuranceClaimsService insuranceClaimsService;


    @Autowired
    private ClaimResponseWebhooks claimResponseWebhooks;

    @Autowired
    private WebHookClient webHookClient;


    @Autowired
    private ClaimPaymentHelper claimPaymentHelper;

    @Autowired
    private NotificationService notificationService;


    private static final List<ExtensionStateCodeEnum> activeClaimStates
            = List.of(ExtensionStateCodeEnum.APPROVED);

    public ClaimsHandlerUtility() {
        super();
    }


    /**
     * Update Status of given Claim and Claim Response objects.
     *
     * @param claim
     * @param claimResponse
     * @param status
     */
    public void updateClaimAndResponseStatus(String claimId, Claim claim, ClaimResponse claimResponse, ExtensionStateCodeEnum status) {
        if (!StringUtils.hasText(claimId)) {
            log.warn("Claim ID is null or empty. Cannot proceed with update.");
            return;
        }
        if (claim == null && claimResponse == null) {
            log.warn("Both Claim and ClaimResponse are null for ClaimID {}. Nothing to update.", claimId);
            return;
        }
        log.info("Updating Status of ClaimID: {}, ClaimResponseId: {} to {}", claimId, (claimResponse != null ? claimResponse.getIdPart() : "N/A"), status.getDisplayName());

        try {
            if (claim != null) {
                updateResource(claim, claimId, status, "Claim"); // Update Claim in FHIR
            }
            if (claimResponse != null) {
                updateResource(claimResponse, claimId, status, "ClaimResponse"); // Update ClaimResponse in FHIR
            }

            // Ensure claimId is valid and exists in the database before updating
            if (insuranceClaimsService.existsByExternalId(claimId)) {
                insuranceClaimsService.updateClaimsStatus(claimId, status);
                log.info("Successfully updated status in the database [claimsTb] for ClaimID: {}", claimId);
            } else {
                log.warn("ClaimID {} does not exist in the database. Skipping DB update.", claimId);
            }
        } catch (Exception e) {
            log.warn("Failed to update ClaimID {} due to error: {}", claimId, e.getMessage(), e);
        }
    }

    public void updateResource(DomainResource resource, String claimId, ExtensionStateCodeEnum status, String resourceType) {
        try {
            log.info("ResourceType: {}, ClaimID: '{}', Latest Version: {}", resourceType, claimId, resource.getMeta().getVersionId());
            extentionService.updateState(resource, status);
            MethodOutcome outcome = fhirClient.update().resource(resource).execute();
            log.info("ResourceType: {}, Update Successful! ClaimID: {}, New Version: {}", resourceType, claimId, outcome.getId().getVersionIdPart());
        } catch (BaseServerResponseException e) {
            if (e.getStatusCode() == 409) { // Handle version conflict
                log.info("ResourceType: {}, Version conflict detected, ClaimId: {}. Fetching latest version...", resourceType, claimId);
                DomainResource latestResource = claimPaymentHelper.getClaimObject(claimId);
                extentionService.updateState(latestResource, status);
                MethodOutcome retryOutcome = fhirClient.update().resource(latestResource).execute();
                log.info("ResourceType: {}, Retry Update Successful! ClaimId: {}, New Version: {}", resourceType, claimId, retryOutcome.getId().getVersionIdPart());
            } else {
                log.warn("ResourceType: {}, Exception while updating ClaimId {}: status: {}, {}", resourceType, claimId, status, e.getMessage());
            }
        }
    }

    /**
     * Call all Webhooks to push Claim Response.
     */

    public void callClaimResponseWebhooks(ClaimResponse claimResponse, String externalClaimId, ExtensionStateCodeEnum actionStatus) {
        String responseJson = fhirParser.encodeToString(claimResponse);
        callResponseWebhooks(responseJson, externalClaimId, claimResponse.getIdPart(), false, actionStatus);
    }

    public void callPaymentWebhooks(String paymentAdviseBundle, String externalClaimId, String claimResponseID, ExtensionStateCodeEnum actionStatus) {
        callResponseWebhooks(paymentAdviseBundle, externalClaimId, claimResponseID, true, actionStatus);
    }

    public void callResponseWebhooks(String bundleResource, String externalClaimId, String responseID, Boolean isPaymentCallBack, ExtensionStateCodeEnum actionStatus) {
        InsuranceClaim icObj = insuranceClaimsService.findByExternalID(externalClaimId);
        String source;
        if (icObj != null) {
            if (!StringUtils.hasText(icObj.getSource())) {
                log.warn("Source is null, claim id {}", externalClaimId);
                source = "SAVANNAH";  // Handle the historical Data. Default call is savannah
            } else {
                source = icObj.getSource();
            }
            callResponseWebhooksAsync(bundleResource, externalClaimId, responseID, source, isPaymentCallBack, actionStatus);
        } else {
            log.warn("No claim found for claim id {}, Unable to callback payment advise", externalClaimId);
        }
    }

    @Async("virtualThreadExecutor")
    protected void callResponseWebhooksAsync(String bundleResource, String externalClaimId, String responseID, String source, Boolean isPaymentCallBack, ExtensionStateCodeEnum actionStatus) {
        // By Default webhook is enabled
        if (!enableWebhooksClient) { //For dev only not enabled.
            log.info("Skipping webhook, Disabled in Dev env.");
            return;
        }
        try {
            // Get the source from claims
            log.info("Inside callResponseWebhooks: externalClaimId {} isPaymentCallBack {}", externalClaimId, isPaymentCallBack);
            String callbackType = isPaymentCallBack ? PAYMENT : CLAIM;


            // Find the appropriate webhook for the given source
            claimResponseWebhooks.getWebhook().stream()
                    .filter(w -> {
                        String consumer = Optional.ofNullable(w.getConsumer()).map(String::trim).orElse("");
                        String callback = Optional.ofNullable(w.getCallbackType()).map(String::trim).orElse("");
                        log.info("source : {} callbackType : {}", source, callback);
                        return !consumer.isEmpty() && consumer.equalsIgnoreCase(source) &&
                                !callback.isEmpty() && callback.equalsIgnoreCase(callbackType);
                    })
                    .findFirst()
                    .ifPresentOrElse(
                            sWebhook -> {
                                if (enableNotificationService) {
                                    if(!callbackType.equalsIgnoreCase(PAYMENT) && source.equalsIgnoreCase(notificationServiceProvider)){
                                        log.info("Sending to client sent Webhook via notification service to: {}, Type: {}, claimId: {}", source, callbackType, externalClaimId);
                                        notificationService.sendNotification(sWebhook.getConsumer(), actionStatus.name(), externalClaimId, bundleResource );
                                    }
                                } else {
                                    log.info("Sending to client sent Webhook to: {}, Type: {}, claimId: {}", source, callbackType, externalClaimId);
                                    webHookClient.sendCallBack(sWebhook, bundleResource, responseID, externalClaimId).subscribe(
                                            null,
                                            error -> log.error("Webhook delivery failed for claimId: {} - {} - {}", externalClaimId, error.getMessage(), source)
                                    );
                                }
                            },
                            () -> log.warn("No matching webhook found for consumer: {}, Type: {}, claimID: {}. Available webhooks: {}",
                                    source, callbackType, externalClaimId,
                                    claimResponseWebhooks.getWebhook().stream()
                                            .map(w -> "Consumer: " + w.getConsumer() + ", Type: " + w.getCallbackType())
                                            .collect(Collectors.toList()))
                    );

        } catch (Exception e) {
            log.warn("Error sending webhook callback, claimID: {} - {}", externalClaimId, e.getMessage());
        }
    }

    public ClaimResponse getClaimResponse(InsuranceClaim iClaim) {
        // Claim Response
        ClaimResponse claimResponse = null;
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(iClaim.getClaimResponseId())) {
            claimResponse = claimPaymentHelper.getClaimResponseById(iClaim.getClaimResponseId());
        }
        // Temp Fix for the Multiple Claim response object in FHIR
        if (claimResponse == null) {
            claimResponse = claimPaymentHelper.getClaimResponseObject(iClaim.getExternalId());
            if (claimResponse == null) {
                log.warn("⚠️No ClaimResponse found for external claimId: {}", iClaim.getExternalId());
                throw new RuntimeException("Error occurred, ClaimResponse is not found against claimId :" + iClaim.getExternalId());
            }
        }
        return claimResponse;
    }
}
