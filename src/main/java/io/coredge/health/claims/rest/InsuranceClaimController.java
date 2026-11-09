package io.coredge.health.claims.rest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.gson.*;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.ResourceNotAuthorizedException;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.ErpReprocessListClaimsRequest;
import io.coredge.health.claims.models.Predetermination;
import io.coredge.health.claims.models.PredeterminationLogResponse;
import io.coredge.health.claims.models.claims.*;
import io.coredge.health.claims.db.entity.BatchApprovalRequest;
import io.coredge.health.claims.db.repositories.BatchApprovalRequestRepository;
import io.coredge.health.claims.dto.BulkApprovalRequest;
import io.coredge.health.claims.dto.BulkApprovalResponse;
import io.coredge.health.claims.service.*;
import org.springframework.data.domain.Page;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.coredge.health.claims.constants.Constants.EndPoints.*;

@RestController
@RequestMapping(CLAIMS_API_PATH)
@Slf4j
public class InsuranceClaimController {

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private FhirContext fhirContext;

    @Autowired
    private IParser fhirParser;

    @Autowired
    private PostOfficeService postOfficeService;

    @Autowired
    private ClaimResponseService claimService;

    @Autowired
    private ClaimBundleService claimBundleService;

    @Autowired
    private FhirService service;

    @Autowired
    private PredeterminationService predeterminationService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private BulkApprovalService bulkApprovalService;

    @Autowired
    private BatchApprovalRequestRepository batchApprovalRequestRepository;

    // post endpoint to accept a claim object
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createClaim(@RequestBody String claimJson) {
        try {
            Bundle responseBundle = claimBundleService.claimJsonMethod(claimJson);
            String response = fhirParser.encodeResourceToString(responseBundle);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception ex) {
            return new ResponseEntity<>("Invalid input json. ", HttpStatus.BAD_REQUEST);
        }
    }

    // post endpoint to accept a claim object
    @PostMapping(path = BUNDLE, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createClaimBundle(@RequestBody String bundleJson) {
        ResponseEntity<String> responseEntity;
        try {
            Bundle responseBundle = claimBundleService.bundleMethod(bundleJson);
            String response = fhirParser.encodeResourceToString(responseBundle);
            responseEntity = new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            log.warn("!!!!! Error while processing Claim. {}", e.getMessage());
            responseEntity = new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return responseEntity;
    }


    @PostMapping(path = BUNDLE_ASYNC, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createClaimBundleAsync(@RequestBody String bundleJson) {
        ResponseEntity<String> responseEntity = null;
        try {
            String response = claimBundleService.bundleMethodAsync(bundleJson);
            responseEntity = new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            log.warn("!!!!! Error while processing Async Claim. {}", e.getMessage());
            responseEntity = new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return responseEntity;
    }

    // post endpoint to accept a claim object
    @GetMapping(path = BY_FACILITY, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getClaimsByFacility(@RequestParam String facilityCode, @RequestParam String type, @RequestParam(required = false) String claimResponseId) throws JsonProcessingException {
        StringBuilder claimJson = new StringBuilder();
        List<String> claims = postOfficeService.findClaimsByProvider(facilityCode, type, claimResponseId);
        int index = 0;
        List<String> claimsStrList = new ArrayList<String>();
        ObjectMapper mapper = new ObjectMapper();
        for (String claim : claims) {
            index++;
            claimJson.append(claim);
            if (index < claims.size()) claimJson.append(",");
        }
        //TODO: handle it correctly.
        return new ResponseEntity<>("[" + claimJson.toString() + "]", HttpStatus.OK);
    }

    // post endpoint to accept a claim object
    @GetMapping(path = STATUS_BY_CLAIM_RESPONSE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getClaimStatus(@PathVariable String claimResponseId) throws JsonProcessingException {
        String claimJson = "";
        ClaimResponse response = postOfficeService.findClaimResponseById(claimResponseId);
        if (response != null) {
            claimJson = fhirParser.encodeResourceToString(response);
        }

        return new ResponseEntity<>(claimJson, HttpStatus.OK);
    }

    // post endpoint to accept a claim object
    @PostMapping(path = CLAIM_BUNDLE_VALIDATE, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> postClaimBundle(@RequestBody String bundleJson) {
        Bundle bundle;
        try {
            bundle = fhirParser.parseResource(Bundle.class, bundleJson);
        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }

        FhirValidator validator = fhirContext.newValidator();
        ValidationResult result = validator.validateWithResult(bundleJson);

        // Step 3: Check if the validation was successful
        if (!result.isSuccessful()) {
            // If validation fails, return the validation errors
            IBaseOperationOutcome operationOutcome = result.toOperationOutcome();
            return new ResponseEntity<>(fhirParser.encodeResourceToString(operationOutcome), HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(fhirParser.encodeResourceToString(bundle), HttpStatus.OK);
    }

//    @RequiresPermission(resource = ResourceEnum.CLAIMS, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PostMapping(CLAIM_ID_ADJUDICATION_CLAIM)
    public ResponseEntity<?> processClaim(@PathVariable String claimId, @RequestBody AdjudicateRequestBody requestBody) {
      //  try {
            log.info("Claim : Marking ClaimId: {} as {}", claimId, requestBody.getAction());
            List<ClaimResponse.NoteComponent> response = claimService.processClaim(claimId, requestBody, true, false);
            return ResponseEntity.ok(new Gson().toJson(response));
       // } catch (RuntimeException e) {
         //  return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        //}
    }

    //    @RequiresPermission(resource = ResourceEnum.PREAUTHORIZATIONS, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PostMapping(CLAIM_ID_ADJUDICATION_PRE_AUTH)
    public ResponseEntity<?> processPreAuth(@PathVariable String claimId, @RequestBody AdjudicateRequestBody requestBody) {
       // try {
            log.info("PreAuth : Marking ClaimId: {} as {}", claimId, requestBody.getAction());
            List<ClaimResponse.NoteComponent> response = claimService.processPreAuth(claimId, requestBody, true);
            return ResponseEntity.ok(new Gson().toJson(response));
       // } catch (RuntimeException e) {
           // return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
       // }
    }

    @GetMapping(PROCESS_NOTES_CLAIM_ID)
    public ResponseEntity<?> getProcessNotes(@PathVariable String claimId) {
        try {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDateTime.class,
                            (JsonSerializer<LocalDateTime>) (src, t, ctx) -> new JsonPrimitive(src.toString()))
                    .registerTypeAdapter(LocalDateTime.class,
                            (JsonDeserializer<LocalDateTime>) (json, t, ctx) -> LocalDateTime.parse(json.getAsString()))
                    .create();
            return ResponseEntity.ok(gson.toJson(claimService.getProcessNotesWithAudit(claimId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // post endpoint
    @PostMapping(path = REPROCESS_CLAIM_ID, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reprocessClaim(@PathVariable String claimId) {
        try {
            log.info("Reprocessing for ClaimId : {} ", claimId);
            claimBundleService.reProcess(claimId);
            Map<String, String> responseBody = Collections.singletonMap("message", "ReProcess Completed");
            return new ResponseEntity<>(responseBody, HttpStatus.OK);
        } catch (Exception ex) {
            log.warn("Exception while Reprocessing for ClaimId : {} Error : {} ", claimId, ex.getMessage());
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping(path = RESUBMIT_CLAIM_ID, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reSubmit(@PathVariable String claimId) {
        try {
            log.info("Resubmitting for ClaimId : {} ", claimId);
            claimBundleService.reSubmit(claimId);
            Map<String, String> responseBody = Collections.singletonMap("message", "ReSubmit Successful");
            return ResponseEntity.ok(responseBody);
        } catch (Exception e) {
            log.warn("Exception while Resubmitting for ClaimId : {} Error : {} ", claimId, e.getMessage());
            Map<String, String> responseBody = Collections.singletonMap("error", "Exception while Resubmitting : " + e.getMessage());
            return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Endpoint to add supporting documents to a Claim.
     *
     * @param claimId        ID of the Claim resource
     * @param newAttachments List of attachments to be added
     * @return Response containing added document details
     */
    @PatchMapping(value = CLAIM_PATCH_SUPPORTING_DOC, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> updateSupportingDocument(@PathVariable String claimId, @RequestBody List<Map<String, Object>> newAttachments) {
        return ResponseEntity.ok(service.updateSupportingDoc(claimId, newAttachments));
    }

    @PatchMapping(value = CLAIM_PATCH_RELATED_INFO, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> updateRelatedInfo(@PathVariable String claimId, @RequestBody ArrayNode relatedInfo) {
        return ResponseEntity.ok(claimBundleService.updatedRelatedInfo(claimId, relatedInfo));
    }

    /**
     * Endpoint to remove supporting documents by sequence numbers.
     *
     * @param claimId     ID of the Claim resource
     * @param sequenceNos List of sequence numbers to be removed
     * @return Response containing removed sequence numbers
     */
    // @PreAuthorize("ADMIN")
    // @DeleteMapping("/{claimId}/supporting-docs")
    public ResponseEntity<Object> removeSupportingDocs(@PathVariable String claimId, @RequestBody List<Integer> sequenceNos) {
        return ResponseEntity.ok(service.removeSupportingDocs(claimId, sequenceNos));
    }

    @PatchMapping(value = CLAIM_PATCH_CLAIM_INFO, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateClaimRequest(
            @PathVariable("claimId") String claimId,
            @RequestParam Map<String,String> updates) {
        claimBundleService.updateClaimRequestInfo(claimId , updates);
        return ResponseEntity.accepted().build();
    }

    @PostMapping(VALIDATE_PREDETERMINATION_API_PATH)
    public ResponseEntity<Map<String , Object>> validatePredetermination(@PathVariable String code, @RequestBody @Valid Predetermination data) {
        data.setCode(code);
        return ResponseEntity.ok(predeterminationService.responsePredetermination(data));
    }

    @PostMapping(BULK_ACTION)
    public ResponseEntity<BulkClaimResponse> processBulkAction(
            @Valid @RequestBody BulkAdjudicateRequest request) {
        BulkClaimResponse response = claimService.processBulkClaimActions(request, request.getAction(), request.getClaimType());
        return ResponseEntity.ok(response);
    }

    @PostMapping(BUNDLE_RESUBMIT)
    public ResponseEntity<String> modifyClaimBundle(@RequestBody String bundleJson) {
        String result = claimBundleService.updateBundle(bundleJson);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }

    @GetMapping(PREDETERMINATION_LOGS)
    public ResponseEntity<CollectionModel<EntityModel<PredeterminationLogResponse>>>
    fetchAllPredeterminationLogs(@RequestParam(required = false) String claimId, @RequestParam(required = false) Boolean status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                 @RequestParam(defaultValue = "created_date") String sortBy, @RequestParam(defaultValue = "DESC") String direction) {

        return ResponseEntity.ok(predeterminationService.fetchPredeterminationDetails(claimId, status, PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy))));
    }

    @GetMapping(PREDETERMINATION_LOGS_BYID)
    public ResponseEntity<CollectionModel<EntityModel<PredeterminationLogResponse>>> fetchPredeterminationResponse(@PathVariable("claimId") String claimId , @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                                                                                                   @RequestParam(defaultValue = "created_date") String sortBy, @RequestParam(defaultValue = "DESC") String direction) {
        return ResponseEntity.ok(predeterminationService.fetchPredeterminationDetail(claimId, PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy))));
    }

    @PostMapping(MANUAL_ADJUDICATION_CLAIM)
    public ResponseEntity<?> processClaimNewStages(@PathVariable String claimId, @RequestBody AdjudicateRequestBody requestBody) {
        log.info("Manual/Surveillance processing of ClaimId: {} as {}", claimId, requestBody.getAction());
        log.info("Request Body {}", requestBody);

        boolean hasPermission =
                permissionService.hasPermission(
                        HealthIdContextHolder.userId(), ResourceEnum.CLAIMS, ActionEnum.EDIT);
        log.info("Role based user Permissions: {}", hasPermission);

        if (!hasPermission) {
            throw new ResourceNotAuthorizedException("User is not authorized to perform this action");
        }

        List<ClaimResponse.NoteComponent> response = claimService.processClaimNewStages(claimId, requestBody,false);
        return ResponseEntity.ok(new Gson().toJson(response));
    }


    @PostMapping(REPROCESS_CLAIM)
    public ResponseEntity<String> erpBackReProcess(@RequestBody ErpReprocessListClaimsRequest request) {
        if (request.getClaimIDs().isEmpty()) {
            throw new BadRequestException("Claim IDs is empty");
        }
        claimService.reProcessErpRequestClaimIds(request);
        return ResponseEntity.ok("Accepted");
    }

    /**
     * Bulk approval endpoint for processing multiple claims at once.
     * Supports filtering by packageCode and date range.
     *
     * @param request The bulk approval request containing filter criteria
     * @return Response with counts and details of approved/failed claims
     */
    @PostMapping("/bulk-approve")
    public ResponseEntity<BulkApprovalResponse> bulkApproveClaims(
            @RequestBody @Valid BulkApprovalRequest request) {
        log.info("Bulk approval request for packageCode: {}, dateRange: {} to {}, approveAll: {}",
                request.getShaCode(), request.getStartDate(), request.getEndDate(), request.isApproveAll());

        boolean hasPermission = permissionService.hasPermission(
                HealthIdContextHolder.userId(), ResourceEnum.CLAIM_BULK_APPROVE, ActionEnum.EDIT);
        log.info("Bulk approve permission check: {}", hasPermission);
        if (!hasPermission) {
            throw new ResourceNotAuthorizedException("User is not authorized to perform bulk approval");
        }

        BulkApprovalResponse response = bulkApprovalService.processBulkApproval(request);
        log.info("Bulk approval completed: {} approved, {} failed", response.getTotalApproved(), response.getTotalFailed());
        return ResponseEntity.ok(response);
    }

    /**
     * Bulk approval endpoint for processing claims from an uploaded Excel file.
     * The Excel file should have claim IDs in the first column (header row is skipped).
     * Only claims in MEDICAL_REVIEW, MANUAL_REVIEW, or APPROVED status will be processed.
     *
     * @param file Excel file containing claim IDs
     * @return Response with counts and details of approved/failed claims
     */
    @PostMapping(value = "/bulk-approve/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulkApprovalResponse> bulkApproveFromExcel(
            @RequestParam("file") MultipartFile file) {
        log.info("Excel bulk approval request, file: {}", file.getOriginalFilename());

        boolean hasPermission = permissionService.hasPermission(
                HealthIdContextHolder.userId(), ResourceEnum.CLAIM_BULK_APPROVE, ActionEnum.EDIT);
        log.info("Bulk approve upload permission check: {}", hasPermission);
        if (!hasPermission) {
            throw new ResourceNotAuthorizedException("User is not authorized to perform bulk approval");
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(BulkApprovalResponse.builder()
                    .processingId("N/A")
                    .totalEligible(0)
                    .totalApproved(0)
                    .totalFailed(1)
                    .build());
        }

        BulkApprovalResponse response = bulkApprovalService.processBulkApprovalFromExcel(file);
        log.info("Excel bulk approval completed: {} approved, {} failed", response.getTotalApproved(), response.getTotalFailed());
        return ResponseEntity.ok(response);
    }
    /**
     * Lists eligible claims for bulk approval with pagination.
     * Uses the same filtering criteria as bulk approval processing (serviced_date, single claim item,
     * use=claim, is_phc_claim=false) to ensure the UI preview matches the actual processing results.
     *
     * @param shaCode   SHA code filter (required)
     * @param status    Optional status filter (MEDICAL_REVIEW, MANUAL_REVIEW, APPROVED)
     * @param startDate Start date for serviced_date range (yyyy-MM-dd)
     * @param endDate   End date for serviced_date range (yyyy-MM-dd)
     * @param page      Page number (0-based)
     * @param size      Page size
     * @return Paginated list of eligible claims
     */
    @GetMapping("/bulk-approve/list")
    public ResponseEntity<CollectionModel<EntityModel<ClaimDataResponse>>> listBulkApprovalClaims(
            @RequestParam String shaCode,
            @RequestParam(required = false) String status,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Listing bulk approval eligible claims for shaCode: {}, status: {}, dateRange: {} to {}", shaCode, status, startDate, endDate);

        boolean hasPermission = permissionService.hasPermission(
                HealthIdContextHolder.userId(), ResourceEnum.CLAIM_BULK_APPROVE, ActionEnum.VIEW);
        log.info("Bulk approve list permission check: {}", hasPermission);
        if (!hasPermission) {
            throw new ResourceNotAuthorizedException("User is not authorized to view bulk approval claims");
        }

        return ResponseEntity.ok(bulkApprovalService.listEligibleClaimsForUI(shaCode, status, startDate.toLocalDate(), endDate.toLocalDate(), PageRequest.of(page, size)));
    }

    /**
     * Returns paginated batch approval history.
     */
    @GetMapping("/bulk-approve/batches")
    public ResponseEntity<Page<BatchApprovalRequest>> getBatchHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Fetching batch approval history, page={}, size={}", page, size);

        boolean hasPermission = permissionService.hasPermission(
                HealthIdContextHolder.userId(), ResourceEnum.CLAIM_BULK_APPROVE, ActionEnum.VIEW);
        if (!hasPermission) {
            throw new ResourceNotAuthorizedException("User is not authorized to view batch approval history");
        }

        Page<BatchApprovalRequest> batches = batchApprovalRequestRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return ResponseEntity.ok(batches);
    }

}
