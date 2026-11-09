package io.coredge.health.claims.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consolidated application properties container.
 * Each nested class is a separate Spring component with @RefreshScope.
 * Remove @RefreshScope from any inner class to disable refresh for that group.
 */
public class ApplicationProperties {

    /**
     * AI service properties.
     * Properties: ai.notes.*
     */
    @Getter
    @Setter
    @Component
    @RefreshScope
    public static class Ai {
        @Value("${ai.notes.enable:false}")
        private boolean enable;

        @Value("${ai.notes.base-url:}")
        private String baseUrl;

        @Value("${ai.notes.claim-bundle-uri:}")
        private String claimBundleUri;

        @Value("${ai.notes.auth-token:}")
        private String authToken;

        @Value("${ai.notes.encryption-key:}")
        private String encryptionKey;

        @Value("${ai.notes.retry.duration.second:2}")
        private int retryDurationSecond;

        @Value("${ai.notes.retry.maxAttempt:3}")
        private int retryMaxAttempt;

        @Value("${ai.preauth.enable:false}")
        private boolean preauthAiEnable;

        @Value("${ai.preauth.base-url:}")
        private String preAuthValidateUri;

        @Value("${ai.preauth-bundle-uri:}")
        private String preAuthBundleUri;
    }

    /**
     * Livia integration properties.
     * Properties: livia.*, liviaBaseUrl, liviaAuthToken, etc.
     */
    @Getter
    @Setter
    @Component
    @RefreshScope
    public static class Livia {
        @Value("${livia.integration.enable:false}")
        private boolean integrationEnable;

        @Value("${liviaBaseUrl:}")
        private String baseUrl;

        @Value("${liviaForwardedHeader:}")
        private String forwardedHeader;

        @Value("${liviaAuthToken:}")
        private String authToken;

        @Value("${liviaEncryptionKey:}")
        private String encryptionKey;

        @Value("${liviaIv:}")
        private String iv;

        @Value("${livia.retry.maxRetries:3}")
        private int maxRetries;

        @Value("${livia.retry.delayMs:3000}")
        private long retryDelayMs;
    }

    /**
     * ERP integration properties.
     * Properties: erp.*
     */
    @Getter
    @Setter
    @Component
    @RefreshScope
    public static class Erp {
        @Value("${erp.base.uri:}")
        private String baseUri;

        @Value("${erp.base.update.status.uri:}")
        private String baseUpdateStatusUri;

        @Value("${erp.base.multi.uri:}")
        private String baseMultiUri;

        @Value("${erp.api.key:}")
        private String apiKey;

        @Value("${erp.api.update.key:}")
        private String apiUpdateKey;

        @Value("${erp.claim.bifurcation.enable:false}")
        private boolean bifurcationEnable;

        @Value("${erp.claim.reference.link:}")
        private String referenceLink;

        @Value("${erp.claim.traffic.enable:false}")
        private boolean trafficEnable;
    }

    /**
     * HIE (Health Information Exchange) properties.
     * Properties: HIE_*, hie*
     */
    @Getter
    @Setter
    @Component
    @RefreshScope
    public static class Hie {
        @Value("${HIE_USERNAME:}")
        private String username;

        @Value("${HIE_PASSWORD:}")
        private String password;

        @Value("${HIE_AUTH_URI:}")
        private String authUri;

        @Value("${HIE_KEY:apeiro_apps}")
        private String key;

        @Value("${HIE_REFER:https://eclaims.apeiro-digital.com}")
        private String refer;

        @Value("${hieProviderNameMatch:false}")
        private boolean providerNameMatch;

        @Value("${hie.provider.source:SAFARICOM}")
        private String providerSource;
    }

    /**
     * Safaricom HIE properties.
     * Properties: SAF_HIE_*
     */
    @Getter
    @Setter
    @Component
    @RefreshScope
    public static class SafaricomHie {
        @Value("${SAF_HIE_USERNAME:}")
        private String username;

        @Value("${SAF_HIE_PASSWORD:}")
        private String password;

        @Value("${SAF_HIE_AUTH_URI:}")
        private String authUri;

        @Value("${SAF_HIE_CUSTOM_API:true}")
        private boolean customApi;

        @Value("${SAF_HIE_TOKEN_API_TYPE:POST}")
        private String tokenApiType;
    }

    /**
     * Feature flags for enabling/disabling application features.
     */
    @Getter
    @Setter
    @Component
    @RefreshScope
    public static class FeatureFlags {
        @Value("${claimsManualReview:true}")
        private boolean claimsManualReview;

        @Value("${enable.user.assignment:false}")
        private boolean enableUserAssignment;

        @Value("${finsprint.eligibility.enabled:false}")
        private boolean finsprintEligibilityEnabled;

        @Value("${benefitsRestrictionsEnabled:false}")
        private boolean benefitsRestrictionsEnabled;

        @Value("${shaUserTaskAutoApprove:false}")
        private boolean shaUserTaskAutoApprove;

        @Value("${preAuthDocReq:false}")
        private boolean preAuthDocRequired;

        @Value("${claimDocReq:false}")
        private boolean claimDocRequired;

        @Value("${validateMemberWithHie:false}")
        private boolean validateMemberWithHie;

        @Value("${validatePractitionerWithHie:false}")
        private boolean validatePractitionerWithHie;

        @Value("${validateProviderWithHie:false}")
        private boolean validateProviderWithHie;
    }

    /**
     * Adjudication rules configuration.
     * Properties: interventions.*, los.*, level.*
     */
    @Getter
    @Setter
    @Component
    @RefreshScope
    public static class AdjudicationRules {
        @Value("#{'${interventions.reqDocAuth:}'.split(',')}")
        private List<String> servicesNeedsDocPreAuthApproval;

        @Value("#{'${interventions.reqDocAuth.Claims:}'.split(',')}")
        private List<String> servicesNeedsDocClaimApproval;

        @Value("${los.reqDocAuth.Claims.Ip:4}")
        private int losReqDocAuthIp;

        @Value("${los.reqDocAuth.Claims:30}")
        private int losReqDocAuth;

        @Value("${level.reqDocAuth.Claims:3}")
        private String levelReqDocAuth;
    }

    /**
     * External services endpoints and configuration.
     * Properties: FHIR_*, TERMINOLOGY_*, afyangu*, cpp*, ifs.*
     */
    @Getter
    @Setter
    @Component
    @RefreshScope
    public static class ExternalServices {
        @Value("${FHIR_SERVER_BASE_URL:}")
        private String fhirServerBaseUrl;

        @Value("${TERMINOLOGY_SERVER_BASE_URL:}")
        private String terminologyServerBaseUrl;

        @Value("${afyanguBaseUrl:}")
        private String afyanguBaseUrl;

        @Value("${afyanguPostClaimUrl:}")
        private String afyanguPostClaimUrl;

        @Value("${cppBaseURL:}")
        private String cppBaseUrl;

        @Value("${cppBaseProviderURL:}")
        private String cppBaseProviderUrl;

        @Value("${cpp.api.key:}")
        private String cppApiKey;

        @Value("${ifs.base.uri:}")
        private String ifsBaseUri;

        @Value("${ifs.token.url:}")
        private String ifsTokenUrl;

        @Value("${ifs.client.id:}")
        private String ifsClientId;

        @Value("${ifs.client.secret:}")
        private String ifsClientSecret;

        @Value("${ifs.scope:}")
        private String ifsScope;

        @Value("${ifs.grant.type:}")
        private String ifsGrantType;

        @Value("${ifs.bank.payment.update.uri:}")
        private String ifsBankPaymentUpdateUri;
    }

    /**
     * Azure storage configuration.
     * Properties: azure.storage.blob.*, prefix.folder
     */
    @Getter
    @Setter
    @Component
    @RefreshScope
    public static class AzureStorage {
        @Value("${azure.storage.blob.storage-endpoint:}")
        private String storageEndpoint;

        @Value("${azure.storage.blob.BlobServiceClient:}")
        private String blobServiceClient;

        @Value("${prefix.folder:}")
        private String prefixFolder;
    }

    /**
     * Payment processing configuration.
     * Properties: payment.processing.*
     */
    @Getter
    @Setter
    @Component
    @RefreshScope
    public static class PaymentProcessing {
        @Value("${payment.processing.batch-size:100}")
        private int batchSize;
    }
}
