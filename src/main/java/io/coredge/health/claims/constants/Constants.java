package io.coredge.health.claims.constants;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static io.coredge.health.claims.enums.ExtensionStateCodeEnum.*;

public interface Constants {

    String SYSTEM = "system";
    String CURRENCY_SHILLING = "KES";
    String GLOBAL_CAPITATION_CAPITATION = "Global Budget Capitation";

    String PMF = "pmf";

    interface Scheduler {
        long RELOAD_WEB_HOOK_FIX_RATE = 10 * 60 * 1000; // 10 minutes * 60 seconds * 1000 milliseconds = 600,000 ms
    }

    interface AdminEndPoints {
        String ADMIN_ENDPOINT_PREFIX = "/admin";

        String PROCESS_API_PATH = ADMIN_ENDPOINT_PREFIX + "/process";
        String PROCESS_LIST_API_PATH = "/list";
        String USER_PAI_PATH = "/user";
        String USER_API_BY_USERNAME = USER_PAI_PATH + "/{username}";
        String USER_API_BY_USERNAME_ALL = USER_PAI_PATH + "/all";
        String USER_API_BY_USERNAME_D_ALL = USER_PAI_PATH + "/disabled-deleted/all";
        String USER_API_ALL_BY_RESOURCE = USER_PAI_PATH + "/all/getUsersGroupedByResourceNames";
        String USER_API_ASSIGN_TASKS = USER_PAI_PATH + "/assignTasks/{use}/{status}";
        String USER_API_REPORTING = USER_PAI_PATH + "/reporting";
        String TASK_COUNT_AND_TOTAL_USERS = "/taskCountAndTotalUsers";
        String USER_API_REASSIGN_TASKS = USER_PAI_PATH + "/reassignTask";

        String REVIEWER_ACTIVITY = USER_PAI_PATH + "/reviewer-activity";

        String RELEASE_UNACTIONED_TASK = REVIEWER_ACTIVITY + "/release/{userId}";

        String FILTER = "/filter";

        String USER_API_CREATE_USER = USER_PAI_PATH + "/create";
        String USER_API_BY_ASSIGN_PERMISSION = USER_PAI_PATH + "/assignPermission";
        String USER_API_ASSIGN_KEYCLOAK_ROLES = "/keycloak/assignRoles";
        String USER_API_LIST_KEYCLOAK_ROLES = "/keycloak/listRoles";
        String USER_API_LIST_ACTIONS = USER_PAI_PATH + "/all/permissionWithActions";

        String BPM_API_PATH = ADMIN_ENDPOINT_PREFIX + "/bpm";
        String BPM_DMN_GEN_API_MAPPING = "/generateDMN";

        String INTERVENTION_API_PATH = "/interventions";
        String INTERVENTION_IMPORT_API_MAPPING = "/import";

        String USER_API_CHANGE_STATUS = USER_API_BY_USERNAME + "/changeStatus";
        String USER_API_REMOVE_PERMISSIONS = USER_PAI_PATH + "/delete/permission/{username}";
        String USER_API_DELETE_USER = USER_PAI_PATH + "/delete/{username}";
        String USER_CONSUMER_INTERNAL_API = USER_PAI_PATH + "/admin/internal/userConsumer";

        // Wallet update endpoints
        String USER_API_UPDATE_WALLET = USER_PAI_PATH + "/update/wallet";
        String USER_API_BATCH_UPDATE_WALLETS = USER_PAI_PATH + "/update/wallets/batch";
        String USER_API_GET_USERS_WITHOUT_WALLETS_COUNT = USER_PAI_PATH + "/wallet/count-without-wallets";
        String INTERVENTION_BUSINESS_COMPONENT = INTERVENTION_API_PATH + "/component";
        String INTERVENTION_RULE = INTERVENTION_API_PATH + "/rule";
        String INTERVENTION_GROUP = INTERVENTION_API_PATH + "/group";
        String INTERVENTION_NODE = INTERVENTION_API_PATH + "/node";
        String RULE_CATEGORY = INTERVENTION_API_PATH + "/ruleCategory";
        String DETAIL_API_PATH = INTERVENTION_API_PATH + "/detail";

        // EPORTAL-702
        String BENEFITS_ENDPOINT_PREFIX = "/benefits";
        String BENEFITS_ENDPOINT_DROP_DOWNS = "/drop-downs";
        String BENEFITS_ENDPOINT_ID = "/{id}";
        String BENEFITS_ENDPOINT_SEARCH = "/search";

        String PRIORITY_REVIEW = "priority";
        String PRIORITY_GET_CATEGORY = PRIORITY_REVIEW + "/getCategory";
        String PRIORITY_GET_SUBCATEGORY = PRIORITY_REVIEW + "/getSubCategory{category}";

        String GLOBAL_PROPERTY = "globalProperty";
    }
    interface EndPoints {

        String AUTH_GENERATE_TOKEN = "/auth/generateToken";

        String MASTER_API_REQUEST_PATH = "/master";

        String BENEFIT_MASTER_API_MAPPING = "/benefit-master";
        String BENEFIT_MASTER_API_NAME_HAVING_MAPPING = BENEFIT_MASTER_API_MAPPING + "/intervention-name/having";
        String BENEFIT_MASTER_CATEGORY_CODE_REQUEST_PATH = "/benefit-master/code";
        String BENEFIT_MASTER_API_MAPPING_ALL = BENEFIT_MASTER_API_MAPPING + "/all";
        String BENEFIT_MASTER_API_MAPPING_ALL_PAGE = BENEFIT_MASTER_API_MAPPING_ALL + "/page";
        String BENEFIT_MASTER_API_MAPPING_ALL_LIST = BENEFIT_MASTER_API_MAPPING_ALL + "/list";
        String BENEFIT_MASTER_API_CREATE = BENEFIT_MASTER_API_MAPPING + "/create";
        String BENEFIT_MASTER_API_UPDATE = BENEFIT_MASTER_API_MAPPING + "/update";
        String BENEFIT_MASTER_API_CSV = BENEFIT_MASTER_API_MAPPING + "/upload";
        String BENEFIT_MASTER_API_BY_UID = BENEFIT_MASTER_API_MAPPING + "/{uid}";
        String BENEFIT_MASTER_API_BY_ID = BENEFIT_MASTER_API_MAPPING + "/{id}";

        String CATEGORY_MASTER_API_MAPPING = "/category";
        String CATEGORY_MASTER_API_MAPPING_ALL = CATEGORY_MASTER_API_MAPPING + "/all";
        String CATEGORY_MASTER_API_MAPPING_PAGE = CATEGORY_MASTER_API_MAPPING + "/all/page";

        String COUNTY_MASTER_API_MAPPING = "/county";
        String COUNTY_USER_ASSIGNMENT_ASSIGN = COUNTY_MASTER_API_MAPPING + "/user/assign";
        String COUNTY_USER_ASSIGNMENT_GET = COUNTY_MASTER_API_MAPPING + "/user/{username}";
        String COUNTY_USER_ASSIGNMENT_ALL_PAGE = COUNTY_MASTER_API_MAPPING + "/users/all/page";
        String COUNTY_MASTER_API_MAPPING_ALL = COUNTY_MASTER_API_MAPPING + "/all";

        String CLAIMS_API_PATH = "/claim";
        String CLAIMS_API_MAPPING_ELIGIBILITY = "/eligibility";
        String CLAIMS_INTERNAL_API_PATH = "/internal/bundles";
        String CLAIM_PATCH_SUPPORTING_DOC = "/{claimId}/supporting-info";
        String CLAIM_PATCH_RELATED_INFO = "/{claimId}/related-info";
        String CLAIM_PATCH_CLAIM_INFO = "/update/{claimId}";
        String VALIDATE_PREDETERMINATION_API_PATH = "/predetermination/{code}";
        String BULK_ACTION = "/bulk-action";

        String REPORT_API_PATH = "/report";

        String HIE_REGISTRY_PATH = "/hie";
        String WF_PATH = "/wf";

        String ADJUDICATION_API_PATH = "/adjudication";
        String ADJUDICATION_API_MAPPING = "/getJson";

        String PROVIDER_MASTER_API_MAPPING = "/provider";
        String PROVIDER_MASTER_API_MAPPING_ID = PROVIDER_MASTER_API_MAPPING + "/level";
        String PROVIDER_MASTER_API_MAPPING_ALL = PROVIDER_MASTER_API_MAPPING + "/all";
        String PROVIDER_MASTER_API_SEARCH = PROVIDER_MASTER_API_MAPPING + "/search";
        String PROVIDER_MASTER_API_GET_BY_ID = PROVIDER_MASTER_API_MAPPING + "/{id}";

        String FACILITY_MASTER_API_MAPPING_PAGE = PROVIDER_MASTER_API_MAPPING + "/all/page";
        String WF_ERP_CALL_BACK_MAPPING = "/claimResponse";
        String WF_GET_CLAIM_RULES = "/claimRules";
        String WF_GET_PREAUTH_RULES = "/preAuthRules";

        String FHIR_PATH = "/fhir";
        String FHIR_API_CLAIM = "/claim";
        String FHIR_API_CLAIM_RESPONSE = "/claim-response";
        String FHIR_API_PATIENT = "/patient";
        String FHIR_API_ORGANIZATION = "/organization";
        String FHIR_API_CLAIM_RES_BY_CLAIM_ID = "/ClaimResponse/ClaimId";
        String FHIR_API_REPLAY_CALLBACK = "/replayCallBack";
        String FHIR_API_CODE_SYSTEM = "/codeSystem";
        String FHIR_API_CODE_SYSTEM_BY_ID = "/codeSystem/{id}";
        String FHIR_API_CODE_SYNC_LINE_ITEMS = "/Sync/LineItems";
        String CLAIM_STATUS_BULK_FHIR_API = "bulk/claim-status";

        String CLAIMS_DATA_API_PATH = "/insurance-claim";
        String CLAIMS_DATA_API_AUDIT_UPDATE = "/audit/update";
        String CLAIMS_DATA_API_SURVEILLANCE_FLAG = "/surveillance/flag";
        String CLAIMS_DATA_API_SURVEILLANCE_LOGS = "/surveillance/logs/{claimId}";
        String CLAIMS_DATA_API_BY_ID = "/claim-id";
        String CLAIMS_DATA_API_ALL_PAGE = "/all/page";
        String CLAIMS_DATA_API_ALL_PAGE_CLAIM = "/all/page-claim";
        String CLAIMS_DATA_API_ALL_PAGE_CLAIM_UPDATE = "/all/page-claim/update";
        String CLAIMS_DATA_API_BY_CLAIM_ID = "/internal/claimObject";
        String CLAIMS_PAYMENT_DETAIL_BY_CLAIM_ID = "/internal/paymentDetail/{claimId}";
        String CLAIMS_DATA_API_ITEMS = "/claimItems/{claimId}";
        String CLAIMS_DATA_API_ALL_PHC_CLAIM_PAGE = "/all/PHC/Claim";
        String CLAIMS_DATA_API_TNT_ALL_PAGE_CLAIM = "/tnt/all/page-claim";

        String PRACTITIONER_MASTER_API_MAPPING = "/practitioner-details";
        String PRACTITIONER_MASTER_API_MAPPING_REG = PRACTITIONER_MASTER_API_MAPPING + "/registration-no";
        String PRACTITIONER_MASTER_API_MAPPING_ALL = PRACTITIONER_MASTER_API_MAPPING + "/all";
        String PRACTITIONER_MASTER_API_MAPPING_PAGE = PRACTITIONER_MASTER_API_MAPPING + "/all/page";

        String PATIENT_API_MAPPING = "/patient";
        String PATIENT_API_MAPPING_ALL_PAGE = "/all/page";
        String PATIENT_API_HISTORY_BY_PATIENT_ID = "/History/{patientId}";
        String PATIENT_API_HISTORY_BY_HOUSEHOLD = "/History/HouseHold";
        String PATIENT_API_MAPPING_SEARCH = "/search/{value}";
        String PATIENT_API_GET_BY_ID = "/{claimId}";
        String PATIENT_API_UPDATE_EXTERNAL = "external/{patientId}";

        String DIAGNOSIS_API_PATH = "/diagnosis";
        String DIAGNOSIS_CLAIMID_API_PATH = "/{claimId}";

        String PERMISSIONS_API_MAPPING = "/permissions";
        String PERMISSIONS_API_BY_USERNAME = "/{username}";

        String DOCUMENTS_API_PATH = "/documents";
        String DOCUMENTS_CLAIMID_API_PATH = "/{claimId}";
        String PROCESS_NOTES_API_REQUEST_PATH = "/ProcessNotes";
        String PROCESS_NOTES_API_GET_BY_CLAIM_ID = "/{claimId}";
        String PROCESS_NOTES_API_HISTORY_DATA = "/Sync";

        String SAVE_SUPPORTING_INFO_API_PATH = "/testClaimDocUpload";

        // practitioner
        String PRACTITIONER_API_MAPPING = "/practitioner";
        String PRACTITIONER_ALL_PATH = "all";
        String PRACTITIONER_ALL_PAGE_PATH = "all/page";
        String PRACTITIONER_REGNO_PATH = "regNo";

        String PAYMENT_API_PATH = "/Payments";
        String PAYMENT_NOTICE_MAPPING = "/CallBack/ClaimId";

        //PHC CLAIM
        String PHC_CLAIM_API_PATH = "/phc-claim";
        String PHC_CLAIM_API_GET_COUNTY_WITH_PROVIDER_COUNT = "/county-with-providercount/{month}";
        String PHC_CLAIM_API_GET_PROVIDER_WITH_CLAIMS_COUNT = "/provider-with-claimscount/{county}/{month}";
        String PHC_CLAIM_API_GET_CLAIMS_SUMMARY = "/claims-summary/{provider}/{county}/{month}/{saveFlag}";
        String PHC_CLAIM_EXPORT_SUMMARY = "/export/{provider}/{county}/{month}";
        String PHC_CLAIM_API_GET_COUNTY_WISE_PROVIDER = "/county-wise-provider/{month}";
        String PHC_CLAIM_API_GET_COUNTRY_WISE_SUMMARY = "/country-wise-summary/{country}/{month}";
        String PHC_CLAIM_COUNTRY_WISE_EXPORT_SUMMARY = "/country-wise-summary/export/{country}/{month}";
        String PHC_CLAIM_API_CALCULATION_ALL = "/calculation/{month}";
        String PHC_CLAIM_API_SEND_FOR_PAYMENTS_ALL = "/send-payments/{month}";

        // PHC Payment Approval API
        String PHC_PAYMENT_API_PATH = "/phc-payments";
        String PHC_PAYMENT_BATCH = "/batches";
        String PHC_PAYMENT_VIEW_BATCH = "/view-batch/{batchId}";
        String PHC_PAYMENT_BATCH_BY_ID = "/batch/{batchId}";
        String PHC_PAYMENT_FACILITY_BY_ID = "/facility/{facilityPaymentId}";
        String PHC_PAYMENT_FACILITY_ACTION = "/facility/action";
        String PHC_PAYMENT_FACILITY_BY_BATCH = "/facility/batch/{batchId}";
        String PHC_PAYMENT_ERP_SUBMIT = "/erp/submit";
        String PHC_PAYMENT_CALCULATION = "/calculate";
        String PHC_MARK_BATCH_PAYMENT_COMPLETED = "/batch/mark-completed";
        // PHC Reports
        String PHC_PAYMENT_REPORTS_CLAIMS = "reports/claim-summary/{paymentId}";
        String PHC_PAYMENT_REPORTS_EXPORT_BATCHES = "reports/export/county-wise/{month}";
        String PHC_PAYMENT_REPORTS_EXPORT_PROVIDERS = "reports/export/provider-wise/{batchId}";
        String PHC_PAYMENT_REPORTS_EXPORT_CLAIMS = "reports/export/claim-summary/{paymentId}";
        String PHC_PAYMENT_REPORTS_EXPORT_FACILITIES = "reports/export/facilities/{yearMonth}";
        String PHC_PAYMENT_REPORTS_EXPORT_CLAIMS_BY_BATCH = "reports/export/claims/batch/{batchId}";

        // PHC Background Processing (Cursor-based)
        String PHC_BACKGROUND_PROCESS_STATUS = "/background-process/{cursorId}";
        String PHC_BACKGROUND_PROCESSES_LIST = "/background-processes";
        String PHC_BACKGROUND_PROCESS_RETRY = "/background-process/{cursorId}/retry";
        String PHC_BACKGROUND_PROCESS_CANCEL = "/background-process/{cursorId}/cancel";

        String COUNTY_POPULATION_BASE_URL = "/api/county-population";

        String CREATE_OR_UPDATE_COUNTY_POPULATION = "/";
        String GET_COUNTY_POPULATION = "/{county}/{monthName}";
        String GET_ALL_COUNTY_POPULATIONS = "/{monthName}";
        String DELETE_COUNTY_POPULATION = "/{county}/{monthName}";

        String USER_VERIFY_PERMISSION = "/verify/assigned/user/{claimId}/{resource}/{action}";

        // Mombasa dashboard
        String FETCH_DASHBOARD_API_PATH = "/dashboard";
        String FETCH_DASHBOARD_DATA = "/fetch-data";
        String SAVE_DASHBOARD_DATA = "/save-data";
        String DELETE_DASHBOARD_DATA = "/delete-data/{id}";

        // SUBPACKAGE
        String SUBPACKAGE_API_BASEURL = "/subPackage";
        String SUBPACKAGE_API_SUBPACKAGEID = "/{subPackageId}";

        // Insurance Claim Controller
        String BUNDLE = "/bundle";
        String BUNDLE_RESUBMIT = "/resubmit";
        String BUNDLE_ASYNC = "/bundle/async";
        String BY_FACILITY = "/byFacility";
        String STATUS_BY_CLAIM_RESPONSE = "/status/{claimResponseId}";
        String CLAIM_BUNDLE_VALIDATE = "/bundle/validate";
        String CLAIM_ID_ADJUDICATION_CLAIM = "/{claimId}/adjudicate-claim";
        String CLAIM_ID_ADJUDICATION_PRE_AUTH = "/{claimId}/adjudicate-preauth";
        String PROCESS_NOTES_CLAIM_ID = "/process-notes/{claimId}";
        String REPROCESS_CLAIM_ID = "/reprocess/{claimId}";
        String RESUBMIT_CLAIM_ID = "/resubmit/{claimId}";
        String PREDETERMINATION_LOGS = "/predeterminationLog";
        String PREDETERMINATION_LOGS_BYID = "/predeterminationLog/{claimId}";
        String MANUAL_ADJUDICATION_CLAIM = "/manual/adjudicate-claim/{claimId}";
        String REPROCESS_CLAIM = "/erp/reprocess";

        // Afyangu Async Controller
        String AFYANGU = "/afyangu";

        // IPS path
        String IPS_API_PATH = "/ips";
        String IPS_API_MAPPING = "/{bundleId}";

        // KNS API Routing
        String KNS_COVERAGE = "/coverage";
        String KNS_VALIDATE = "/validate";
        String REPORT_DASHBOARD = "/report/dashboard";
        String REPORT_DASHBOARD_FILTERS = "/filters";
        String REPORT_DASHBOARD_SUGGEST_FIDCODE = "/suggestFidCodes";
        String REPORT_DASHBOARD_SUBCOUNTY = "/subCounties";
        String REPORT_DASHBOARD_All = "/getAllFilters";
        String REPORT_DASHBOARD_DATA = "/fetchClaimReport";
        String REPORT_DASHBOARD_DOWNLOAD_EXCEL = "/download/excel";
        String REPORT_DASHBOARD_DOWNLOAD_PDF = "/download/pdf";

        //BMS
        String BMS_BENIFIT = "/bms/benefits";
        String BMS_BENIFIT_ID = "/{id}";
        String BMS_BENIFIT_CODE = "/code/{code}";
        String BMS_BENIFIT_NAME = "/name/{name}";
        String BMS_BENIFIT_NAME_DELETE = "/code/{code}";

        String BMS_ELIGIBILITY = "bms/eligibility";
        String BMS_ElIGIBILITY_PROCESS = "/process";
        String BMS_ELIGIBILITY_UPLOAD = "/upload";

        String BMS_ID = "/{id}";
        String BMS_JOB_GROUPS = "/bms/job-groups";
        String BMS_JOB_GROUPS_ID = BMS_ID;
        String BMS_JOB_GROUPS_UPDATE_ID = BMS_ID;
        String BMS_JOB_GROUPS_ID_DELETE = BMS_ID;

        String BMS_PATIENTS = "/bms/patients";
        String BMS_PATIENTS_SEARCH = "/search";

        String BMS_SCHEME_DETAILS = "/bms/scheme-details";
        String BMS_SCHEME_DETAILS_ID = BMS_ID;
        String BMS_SCHEME_DETAILS_UPDATE_ID = BMS_ID;
        String BMS_SCHEME_DETAILS_DELETE_ID = BMS_ID;
        String BMS_SCHEME_DETAILS_BENEFITS_ID = "/benefits/{id}";
        String BMS_SCHEME_DETAILS_JOB_GROUPS_ID = "/job-groups/{id}";

        String BMS_SCHEME_MEMBER = "/bms/scheme-members";
        String BMS_SCHEME_MEMBER_ID = BMS_ID;
        String BMS_SCHEME_MEMBER_UPDATE_ID = BMS_ID;
        String BMS_SCHEME_MEMBER_DELETE= BMS_ID;
        String BMS_SCHEME_MEMBER_PRINCIPAL_DEPENDENT_WITH_LIMITS = "/{id}/details";

        String SCHEME_MEMBER_LIMIT = "/bms/scheme-member-limit";
        String SCHEME_MEMBER_LIMIT_PROCESS_CLAIM = "/process-claim";

        // CLAIM AI Notes
        String CLAIM_AI_NOTES = CLAIMS_API_PATH + "/ai/notes";
        String CLAIM_AI_NOTES_CALL_BACK = "/callback";
        String CLAIM_AI_NOTE_USER_REVIEW = "/user/review";
        String CLAIM_AI_NOTES_MANUAL_TRIGGER = "/manual/trigger/{claimId}";

        // Claim AI Trigger Management
        String CLAIM_AI_TRIGGER_CREATE = "/trigger";
        String CLAIM_AI_TRIGGER_GET_BY_ID = "/trigger/{id}";
        String CLAIM_AI_TRIGGER_GET_ALL = "/trigger";
        String CLAIM_AI_TRIGGER_UPDATE = "/trigger/{id}";
        String CLAIM_AI_TRIGGER_PROCESS = "/trigger/process";

        // AI Validation

        String AI_VALIDATION_BASE_ENDPOINT = "/ai-validation";
        String AI_VALIDATION_ID = "/{id}";
        String AI_VALIDATION_DROP_DOWN = "/dropdown";
        String AI_VALIDATION_BULK_MAPPING = "/bulk-mapping";

        // AI Route
        String AI_ROUTE_BASE_ENDPOINT = "/ai-routes";
        String AI_ROUTE_ID = "/{id}";

    }

    interface ClaimFundType {
        String ECCIF = "eccif";
        String SHIF = "shif";
        String PHC = "phc";
        String PMSF = "pfms";
    }

    interface WorkflowParams {
        String VAR_NOTES = "notes";
        String VAR_BUNDLE_ID = "bundleId";
        String VAR_CLAIM_ID = "claimId";
        String VAR_USER_ID = "UserId";
        String VAR_TRACE_ID = "traceId";
        String VAR_STATUS = "status";
        String VAR_CLAIM_RESPONSE_ID = "claimResponseId";
        String VAR_STATE = "state";
        String VAR_INTERVENTIONS = "interventions";
        String VAR_IS_RETURN_TO_SENDER = "isReturnToSender";
        String VAR_IS_APPROVED_BY_DOC = "isApprovedByDoc";
        String VAR_IS_DOC_APPROVAL_REQ = "isDoctorApprovalRequired";
        String VAR_START_DATE = "startDate";
        String VAR_END_DATE = "endDate";
        String VAR_GENDER = "gender";
        String VAR_AGE = "age";
        String VAR_HAS_PRE_AUTH = "hasPreAuth";
        String VAR_INDIVIDUAL_USAGE_CNT = "individualUsage";
        String VAR_SERVICE_CATEGORY = "serviceCategory";
        String VAR_SHA_CODE = "code";
        String VAR_ITEM_SEQUENCE = "sequence";
        String VAR_CLAIM_AMT = "claimAmount";
        String VAR_LOS = "los";
        String VAR_INTERVENTION_USE_CNT = "interventionUsageCount";
        String VAR_HOUSE_HOLD_USE_CNT = "householdUsage";
        String VAR_INDIVIDUAL_USAGE = "individualUsage";
        String VAR_FACILITY_CATEGORY = "facilityCategory";
        String VAR_SEQUENCE = "sequence";
        String VAR_IS_AUTO_APPROVED = "isAutoApproved";
        String VAR_IS_PHC_CLAIM = "isPhcClaim";
        String VAR_IS_CIVIL_SERVANT_CLAIM = "isCivilServantClaim";
        String VAR_IS_CIVIL_SERVICE_APPROVED = "isCivilServiceApproved";
        String VAR_BIRTHDATE = "birthDate";
        String VAR_STATUS_PROCESSING = "processing";
        String VAR_STATUS_APPROVED = "Approved";
        String VAR_STATUS_MANUAL_REVIEW = "Manual Review";
        String VAR_OUTPATIENT = "outpatient";
        String VAR_INPATIENT = "inpatient";
        String VAR_EMERGECNY = "emergency";
        String VAR_CLAIM_TYPE = "claimType";
        String VAR_IS_EFWA_APPROVED = "isEfwaApproved";
        String VAR_IS_EFWA_REQUIRE_DOCTOR_REVIEW = "isEwaRequireDoctorReview";
        String VAR_IS_EFWA_ENABLED = "isEfwaEnabled";
        String VAR_AI_DECISION = "aiDecision";
        String VAR_AI_DECISION_FLAG = "aiDecisionFlag";
        String VAR_SUPPORT_DOC_PRESENT = "isSupportDocumentPresent";
        String VAR_MANUAL_REVIEW_REQ = "isManualReviewRequired";
        String VAR_SURVEILLANCE_REQ = "isSurveillanceRequired";
        String VAR_MISSING_DOC = "isMissingDoc";
        String VAR_STATUS_REJECT = "Rejected";
        String VAR_IS_MANUAL_APPROVE = "isManualApproved";
        String VAR_IS_SURVEILLANCE_APPROVE = "isSurveillanceApproved";
        String ZEEBE_GATEWAY_IP = "gatewayIp";
        String ACTIVE = "ACTIVE";

    }

    interface HandlerMethod {
        String CLAIM_HANDLER = "claimsHandler";
        String PRE_AUTH_HANDLER = "preAuthHandler";
        String HANDLE_CLAIM_AUTO_ADJ_FAIL = "handleClaimAutoAdjFail";
        String DOCTOR_APPROVAL_HANDLER = "doctorApprovalHandler";
        String PRE_AUTH_SHA_REVIEW_START = "preAuthShaReviewStart";
        String PRE_AUTH_ASSIGNED_TO_DOC_START_EVENT = "preAuthAssignedToDocStartEvent";
        String HANDLE_PRE_AUTH_APPROVED_EVENT = "handlePreAuthApprovedEvent";
        String HANDLE_CLAIM_APPROVED_EVENT = "handleClaimApprovedEvent";
        String HANDLE_PRE_AUTH_RETURNED_EVENT = "handlePreAuthReturnedEvent";
        String HANDLE_CLAIM_RETURNED_EVENT = "handleClaimReturnedEvent";
        String AUTO_ADJUDICATION_HANDLER = "autoAdjudicationHandler";
        String HANDLE_PHC_CLAIM_APPROVED_EVENT = "handlePHCClaimApprovedEvent";
        String CIVIL_SERVICE_HANDLER = "civilServiceHandler";
        String START_PAYMENT_TO_ERP = "startPaymentToERP";
        String PRE_AUTH_CIVIL_HANDLER = "preAuthCivilHandler";
        String SEND_CLAIM_FOR_PAYMENT = "sendClaimForPaymentToERP";
        String AI_NOTES_REQUEST = "sendEfwaRequest";
        String AI_NOTES_RESPONSE = "receiveEfwaNotes";
        String CLAIM_ASSIGNED_TO_MANUAL_REVIEW_START_EVENT = "claimAssignedToManualStartEvent";
        String SEND_PREAUTH_EFWA_REQUEST = "sendPreAuthEfwaRequest";
        String PROCESS_INITIALIZER = "processInitializer";
        String CHECK_EFWA_ELIGIBILITY_HANDLER = "checkEfwaEligibilityHandler";
        String MISSING_DOC_CALL_BACK = "missingDocCallBack";
        String REJECTED_CLAIM_CALL_BACK = "rejectedClaimCallBack";
        String POST_SAVE_VALIDATION_HANDLER = "postSaveValidationHandler";
        String HANDLE_CLAIM_REJECTED_BY_RULE = "handleClaimRejectedEvent";
    }

    enum ClaimLineItemStatus {
        PASSED, REJECTED, PARTIAL, SURVEILLANCE
    }

    interface Request {
        String AUTHORIZATION = "Authorization";
        String BEARER = "Bearer ";
        String X_REQUEST_SOURCE = "x-request-source";
        String BASIC_AUTH = "Basic ";
        String API_KEY = "apikey ";
        String X_API_KEY = "X-API-KEY";
        String BASIC_AUTH_HEADER = "Basic ";
    }

    interface SecurityConstant {
        Set<@NotNull String> ALLOWED_ROLES = Set.of("eclaim_protal_access", "claim_engine_access");
        String BASE_URL_PATTERN = "/api/**";
        String[] SECURITY_BYPASS_URL_PATTERN = {
                "/swagger-ui/**",
                "/claimEngine/actuator/prometheus",
                "/claimEngine/actuator/health/readiness",
                "/claimEngine/actuator/health/liveness",
                "/v3/api-docs/**",
                "/claim/bundle",
                "/claim/status/**",
                "/fhir/replayCallBack**",
                "/claim/*/supporting-info",
                "/wf/claimResponse",
                "/patient/external/**",
                "/wf/claimRules",
                "/wf/preAuthRules",
                "/wf/erp/reprocess",
                "/wf/erpFallBack/reprocess",
                "/wf/erpFallBack/prepareOnly",
                "/wf/erpFallBack/batchPush",
                "/wf/paidStatus/upload",
                "/wf/paidStatus/update",
                "/fhir/ClaimResponse/ClaimId",
                "/master/provider/**",
                "/Payments/CallBack/ClaimId",
                "/admin/auth/generateToken",
                "/claim/predetermination",
                "/claim/predetermination/*",
                "/claim/resubmit",
                "/claim/erp/reprocess",
                "/claim/*/related-info",
                "/claim/update/*",
                "/fhir/bulk/claim-status",
                "/admin/process/startProcess",
                "/admin/process/recallPreAuth/**",
                "/bms/**",
                "/bms/scheme-details/**",
                "/bms/scheme-member-limit/**",
                "/bms/eligibility/upload/**",
                "/bms/eligibility/process/**",
                "/insurance-claim/internal/claimObject",
                "/patient-utilization/**",
                "/insurance-claim/tnt/all/**",
                "/actuator/refresh-all",
                "/bulk-approve",
                "/bulk-approve/list",
                "/payments/callback/**"
        };
    }

    interface PaymentType {
        String COMPLETE = "complete";
        String PARTIAL = "partial";
        String CLAIM = "CLAIM";
        String PAYMENT = "PAYMENT";
    }

    interface SubType {
        String EMERGENCY = "emr";
        String INPATIENT = "ip";
        String OUTPATIENT = "op";
    }

    interface PaymentStatus {
        String PAID = "Paid";
        String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
        String PAYMENT_DECLINED = "PAYMENT_DECLINED";
        String UNPAID = "unpaid";
    }

    interface Use {
        String PREAUTHORIZATION = "Preauthorization";
        String CLAIM = "Claim";

    }

    interface CppClaimType {
        String PRE_AUTH = "PRE_AUTH";
        String SUBMISSION = "SUBMISSION";
    }

    interface WEBHOOK_STATUS {
        String AFYANGU = "AFYANGU";
        String CPP = "CPP";
    }

    List<ExtensionStateCodeEnum> CLAIM_APPROVED_EXTENSION_STATES = List.of(ExtensionStateCodeEnum.CLINICAL_REVIEW,
            ExtensionStateCodeEnum.MEDICAL_REVIEW, ExtensionStateCodeEnum.APPROVED,
            SENT_FOR_PAYMENT_PROCESSING, PAYMENT_COMPLETED,
            ExtensionStateCodeEnum.MANUAL_REVIEW, ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE);
    List<ExtensionStateCodeEnum> APPROVED_EXTENSION_STATES = List.of(ExtensionStateCodeEnum.CLINICAL_REVIEW,
            ExtensionStateCodeEnum.MEDICAL_REVIEW, ExtensionStateCodeEnum.APPROVED,
            SENT_FOR_PAYMENT_PROCESSING, PAYMENT_COMPLETED,
            ExtensionStateCodeEnum.MANUAL_REVIEW, ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE);

    List<String> GOVERNMENT_FACILITIES_TYPES = List.of("COUNTY GOVERNMENT", "GOVERNMENT OF KENYA",
            "GOVERNMENT OF KENYA (GOK)");
    List<String> FACILITY_LEVEL_ALLOWED_FOR_SHA_01 = List.of("level 5", "level 6");
    String SHA_01_INTERVENTION_CODE = "SHA-01";

    String SHA_03_AGE_VALIDATION = "SHA-03-003";

    interface Statuses {
        String ACTIVE = "S1";
        String PENDING = "S2";
        String DELETED = "S3";
        String DISABLED = "S4";

        String ACTIVE_LABEL = "Active";
        String PENDING_LABEL = "Pending";
        String DELETED_LABEL = "Deleted";
        String DISABLED_LABEL = "Disabled";

        Long ACTIVE_ID = 1L;
        Long PENDING_ID = 2L;
        Long DELETED_ID = 3L;
        Long DISABLED_ID = 4L;
    }

    interface BMSStatuses {
        String IN_REVIEW = "S11";
        String SENT_BACK = "S4";
        String SENT_TO_SURVEILLANCE = "S6";
        String CLINICAL_REVIEW = "S12";
        String PAYMENT_COMPLETED = "S10";
        String SENT_FOR_PAYMENT_PROCESSING = "S8";
        String APPROVED = "S3";
        String REJECTED = "S5";
        String MEDICAL_REVIEW = "S13";
        String QUEUED = "S14";
        String DECLINED = "S15";
    }

    interface BenefitCode {
        String INPATIENT = "IP";
        String OUTPATIENT = "OP";
        String MATERNITY = "MAT";
        String OPTICAL = "OPT";
        String DENTAL = "D";
    }

    interface BenefitUsedAmount {
        String INPATIENT = "UsedAmountInpatient";
        String OUTPATIENT = "UsedAmountOutpatient";
        String MATERNITY = "UsedAmountMaternity";
        String OPTICAL = "UsedAmountOptical";
        String DENTAL = "UsedAmountDental";
    }

    interface DeveloperMessage {
        String FIELD_REQUIRED = "FIELD_REQUIRED";
        String UUID_MISSMATCH = "UUID_MISSMATCH";
    }

    interface ClaimStatuses {
        String PENDING = "PENDING";
        String APPROVED = "APPROVED";
        String REJECTED = "REJECTED";
        String PARTIAL = "PARTIAL";
        String IN_REVIEW = "IN_REVIEW";
        String SENT_BACK = "SENT_BACK";
        String SENT_TO_SURVEILLANCE = "SENT_TO_SURVEILLANCE";
        String CLINICAL_REVIEW = "CLINICAL_REVIEW";
        String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
        String SENT_FOR_PAYMENT_PROCESSING = "SENT_FOR_PAYMENT_PROCESSING";
        String MEDICAL_REVIEW = "MEDICAL_REVIEW";
        String QUEUED = "QUEUED";
        String DECLINED = "DECLINED";
    }

    interface ClaimTransactionOperation {
        String CREDIT = "CR";
        String DEBIT = "DR";
    }

    interface PreauthorizationDetails {
        String IS_PREAUTHREQURIED = "isPreauthRequired";
        String PREAUTH_ID = "preauthId";
        String PREAUTH_STATUS = "preauthStatus";
        String PREAUTH_EXPIRY_DATE = "preauthExpiryDate";
    }

    interface BenefitsConfig {
        String HOUSEHOLD = "household";
        String INDIVIDUAL = "individual";
        String AMOUNT = "amount";
        String COUNT = "count";
        String LEVEL = "level";
        String LOS = "los";
        String AGE = "age";
    }

    enum RequestSource {
        LIVIA
    };

    String HEADER_CONTENT_TYPE = "Content-Type";
    String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

    String PARAM_GRANT_TYPE = "grant_type";
    String PARAM_SCOPE = "scope";
    String PARAM_CLIENT_ID = "client_id";
    String PARAM_CLIENT_SECRET = "client_secret";

    String HEADER_AUTHORIZATION = "Authorization";
    String BEARER_PREFIX = "Bearer ";

    interface HTTP_STATUS {
        String CODE_201 = "201";
        String CODE_409 = "409";
        String CODE_202 = "202";
        String CODE_422 = "422";
        String CODE_409_ERROR = "\"status_code\": \"409\"";
        String CODE_400_ERROR = "\"status_code\": \"400\"";
    }

    interface AI_USE {
        String CLAIM_USE = "Claim";
        String PRE_AUTH_USE = "Preauthorization";
    }

    // EPORTAL-1356
    interface CATEGORY {
        String CREATE_CATEGORY = "/admin/create/category";
        String CREATE_CODE = "/admin/create/reason-code";
        String GET_ALL_REASON_CODES = "admin/get/reason-codes";
        String GET_CATEGORIES = "/admin/get/categories";
        String FIND_BY_ID = "admin/update/reason-code/{id}";
        String GET_REASON_CODES = "admin/get/reason-codes/status/{status}";

    }

    interface WORKLOAD {
        String CLAIM_WORKLOAD = "/claims/workload";
        String CLAIM_WORKLOAD_REPORT = "/{claimId}/workload-report";
    }


    // Restricted intervention code prefixes for Civil Servants
    public static final List<String> CIVIL_SERVANT_RESTRICTED_SHA_CODES = List.of(
            "SHA-03", "SHA-07", "SHA-10");

    // Restricted intervention code prefixes for Normal Patients
    public static final List<String> NORMAL_PATIENT_RESTRICTED_PMF_CODES = List.of(
            "PMF-03", "PMF-07", "PMF-10");

    // Validation error messages
    public static final String CIVIL_SERVANT_INVALID_CODE_MESSAGE = "Submission under the selected intervention code is not permitted for Civil Servants. Please use the appropriate PMF intervention code instead.";

    public static final String NORMAL_PATIENT_INVALID_CODE_MESSAGE = "Submission under the selected intervention code is not permitted for normal patients. Please use the appropriate SHA intervention code instead’.";

    interface BPMN_USER_TASK_MESSAGE {
        String MANUAL_REVIEW = "ManualReviewAction";
        String DOCTOR_REVIEW = "DoctorReviewAction";
        String DECLINE_REVIEW = "DeclineReviewAction";
        String SURVEILLANCE_REVIEW = "SurveillanceAction";
    }

    List<ExtensionStateCodeEnum> RESTRICT_REPROCESS_CLAIM_STATUS = List.of(APPROVED, SENT_FOR_PAYMENT_PROCESSING, PAYMENT_COMPLETED , PAID);
}
