package io.coredge.health.claims.rest.impl;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.dto.AuditUpdateRequest;
import io.coredge.health.claims.dto.SurveillanceFlagRequest;
import io.coredge.health.claims.dto.SurveillanceLogResponse;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.ResourceNotAuthorizedException;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.ClaimHistoryDTO;
import io.coredge.health.claims.models.PaymentDetailResponse;
import io.coredge.health.claims.models.claims.ClaimDataResponse;
import io.coredge.health.claims.models.claims.PatientIdType;
import io.coredge.health.claims.rest.ClaimDataController;
import io.coredge.health.claims.service.InsuranceClaimsService;
import io.coredge.health.claims.service.SurveillanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import io.coredge.health.claims.service.PermissionService;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;

@Slf4j
@RestController
@RequestMapping(Constants.EndPoints.CLAIMS_DATA_API_PATH)
@CrossOrigin
public class ClaimDataControllerImpl implements ClaimDataController {

    @Value("${enable.user.assignment:false}")
    private boolean enableUserAssignment;

    @Autowired
    private InsuranceClaimsService insuranceClaimsService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private SurveillanceService surveillanceService;

    @GetMapping(Constants.EndPoints.CLAIMS_DATA_API_BY_ID)
    @Override
    public ResponseEntity<List<ClaimDataResponse>> findByClaimId(@RequestParam String claimId) {
        return ResponseEntity.ok(insuranceClaimsService.findByClaimID(claimId));
    }

    @GetMapping(Constants.EndPoints.CLAIMS_DATA_API_ALL_PAGE)
    @Override
    public ResponseEntity<CollectionModel<EntityModel<ClaimDataResponse>>> fetchAllPreAuthByPage(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String mflCode, String crId, String shaNumber, String birthCertificate, String nationalId, String accessCode, String providerName, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode ,String county, boolean filtered ,LocalDateTime startDate, LocalDateTime endDate, int page, int size, String sortBy, String direction) {
        PageRequest pageRequest = PageRequest.of(page, size, Helper.createSortObject(sortBy, direction,false));
        log.info("User [Page] Logged In Id: {}", HealthIdContextHolder.userId());
        Helper.validateInputs(claimNumber, nationalId);
        // if logged-in user will have admin access

        boolean hasAdminPermission = permissionService.hasPermission(HealthIdContextHolder.userId(), ResourceEnum.ADMIN, ActionEnum.VIEW);
        if (hasAdminPermission) {
            return ResponseEntity.ok(insuranceClaimsService.fetchAllPreAuthDetails(patientIdType, patientId, claimNumber, patientName, providerName, mflCode, crId, nationalId, shaNumber, birthCertificate, accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, false))));
        }

        // if logged-in user will have pre-authorization access but not admin
        if (enableUserAssignment && filtered) {
            boolean hasPermission = permissionService.hasPermission(HealthIdContextHolder.userId(), ResourceEnum.PREAUTHORIZATIONS, ActionEnum.EDIT);
            log.info("Role based user Permissions for pre-auth: {}", hasPermission);
            if (hasPermission) {
                return ResponseEntity.ok(insuranceClaimsService.fetchAllPreAuthDetailsByRole(
                        claimNumber, patientName, providerName, mflCode, crId, nationalId, shaNumber, birthCertificate,
                        accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate, pageRequest));
            }
            throw new ResourceNotAuthorizedException("User is not authorized to perform this action");
        }
        return ResponseEntity.ok(insuranceClaimsService.fetchAllPreAuthDetails(patientIdType, patientId, claimNumber, patientName, providerName, mflCode, crId, nationalId, shaNumber, birthCertificate, accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, false))));

    }

    @GetMapping(Constants.EndPoints.CLAIMS_DATA_API_BY_CLAIM_ID)
    @Override
    public ResponseEntity<String> findBYClaimIdFhir(@RequestParam String claimId) {
        return ResponseEntity.ok(insuranceClaimsService.fetchByClaimIdFhir(claimId));
    }

    @GetMapping(Constants.EndPoints.CLAIMS_PAYMENT_DETAIL_BY_CLAIM_ID)
    @Override
    public ResponseEntity<PaymentDetailResponse> findPaymentDetailByClaimId(@PathVariable("claimId") String claimId) {
        return ResponseEntity.ok(insuranceClaimsService.findPaymentDetailByClaimId(claimId));
    }

    @GetMapping(Constants.EndPoints.CLAIMS_DATA_API_ALL_PAGE_CLAIM)
    @Override
    public ResponseEntity<CollectionModel<EntityModel<ClaimDataResponse>>>
    fetchAllClaimByPage(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String mflCode, String crId, String shaNumber, String birthCertificate, String nationalId, String accessCode, String providerName, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode, String county,boolean filtered, LocalDateTime startDate, LocalDateTime endDate, int page, int size, String sortBy, String direction) {
        Helper.validateInputs(claimNumber, nationalId);

        boolean hasAdminPermission = permissionService.hasPermission(HealthIdContextHolder.userId(), ResourceEnum.ADMIN, ActionEnum.VIEW);
        log.info("User [page-claim] Logged In Id: {} , Admin :{}", HealthIdContextHolder.userId() , hasAdminPermission);
        // if logged-in user will have admin access
        if (hasAdminPermission) {
            return ResponseEntity.ok(insuranceClaimsService.fetchAllClaimsDetails(patientIdType, patientId, claimNumber, patientName, providerName, mflCode, crId, nationalId, shaNumber, birthCertificate, accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, false))));
        }
        // if logged-in user will have claims access but not admin
        log.info("Reached BEFORE IF: enableUserAssignment={}, filtered={}, userId={}",
                enableUserAssignment, filtered, HealthIdContextHolder.userId());

        if (enableUserAssignment && filtered) {
            boolean hasPermission = permissionService.hasPermission(HealthIdContextHolder.userId(), ResourceEnum.CLAIMS, ActionEnum.EDIT);
            log.info("Role based user Permissions claims: {}", hasPermission);
            if (hasPermission) {
                return ResponseEntity.ok(insuranceClaimsService.fetchAllClaimsDetailsByRole(claimNumber, patientName, providerName, mflCode, crId, nationalId,
                        shaNumber, birthCertificate, accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, false))));
            }
            throw new ResourceNotAuthorizedException("User is not authorized to perform this action");
        }
        return ResponseEntity.ok(insuranceClaimsService.fetchAllClaimsDetails(patientIdType, patientId, claimNumber, patientName, providerName, mflCode, crId, nationalId, shaNumber, birthCertificate, accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, false))));

    }


    // Note this endpoint specific for the track n trace
    @GetMapping(Constants.EndPoints.CLAIMS_DATA_API_TNT_ALL_PAGE_CLAIM)
    @Override
    public ResponseEntity<CollectionModel<EntityModel<ClaimDataResponse>>>
    fetchAllClaimByPageTnt(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String mflCode, String crId, String shaNumber, String birthCertificate, String nationalId, String accessCode, String providerName, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode, String county, boolean filtered, LocalDateTime startDate, LocalDateTime endDate, int page, int size, String sortBy, String direction) {
        Helper.validateInputs(claimNumber, nationalId);
        // Check the user for track n trace Only bind manually
        if(HealthIdContextHolder.userId().equals("TNT")) {
            return ResponseEntity.ok(insuranceClaimsService.fetchAllClaimsDetails(patientIdType, patientId, claimNumber, patientName, providerName, mflCode, crId, nationalId, shaNumber, birthCertificate, accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, false))));
        }
        throw new ResourceNotAuthorizedException("User is not authorized to perform this action");
    }

    // --- Retrieves, modifies, or updates claims from the provider portal.
    @GetMapping(Constants.EndPoints.CLAIMS_DATA_API_ALL_PAGE_CLAIM_UPDATE)
    @Override
    public ResponseEntity<CollectionModel<EntityModel<ClaimDataResponse>>>
    fetchAllUpdatedClaimByPage(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String providerName, String crId, String nationalId, String shaNumber, String accessCode, String fidCode, String level,String shaCode, String shaPackageCode, String county, ExtensionStateCodeEnum status,LocalDateTime startDate, LocalDateTime endDate, int page, int size, String sortBy, String direction) {

        return ResponseEntity.ok(insuranceClaimsService.fetchAllUpdatedClaimsDetails( patientIdType, patientId, claimNumber, patientName, providerName, crId, nationalId, shaNumber, accessCode, fidCode, level , shaCode , shaPackageCode, county, status ,Constants.Use.CLAIM , startDate, endDate, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, false))));
    }



    @GetMapping(Constants.EndPoints.CLAIMS_DATA_API_ITEMS)
    @Override
    public ResponseEntity<List<ClaimHistoryDTO>> fetchClaimLineItems(@PathVariable String claimId) {
        return ResponseEntity.ok(insuranceClaimsService.fetchClaimItems(claimId));
    }

    @GetMapping(Constants.EndPoints.CLAIMS_DATA_API_ALL_PHC_CLAIM_PAGE)
    @Override
    public ResponseEntity<CollectionModel<EntityModel<ClaimDataResponse>>> fetchAllPaymentMechanismClaim(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String mflCode, String crId, String shaNumber, String birthCertificate, String nationalId, String accessCode, String providerName, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode, String county,boolean filtered , LocalDateTime startDate, LocalDateTime endDate, int page, int size, String sortBy, String direction) {
        log.info("User [page-claim-phc] Logged In Id: {}", HealthIdContextHolder.userId());
        Helper.validateInputs(claimNumber, nationalId);

        boolean hasAdminPermission = permissionService.hasPermission(HealthIdContextHolder.userId(), ResourceEnum.ADMIN, ActionEnum.VIEW);
        log.info("User [page-claim] Logged In Id: {} , Admin :{}", HealthIdContextHolder.userId() , hasAdminPermission);
        // if logged-in user will have admin access
        if (hasAdminPermission) {
            return ResponseEntity.ok(insuranceClaimsService.fetchAllClaimsPHC(patientIdType, patientId, claimNumber, patientName, providerName, mflCode, crId, nationalId, shaNumber, birthCertificate, accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate,PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, false))));
        }

        // if logged-in user will have claims access but not admin
        if (enableUserAssignment && filtered) {
            boolean hasPermission = permissionService.hasPermission(HealthIdContextHolder.userId(), ResourceEnum.CLAIMS, ActionEnum.VIEW);
            log.info("Role based user Permissions: {}", hasPermission);
            if (hasPermission) {
                return ResponseEntity.ok(insuranceClaimsService.fetchAllClaimsPHCByRole(claimNumber, patientName, providerName, mflCode, crId, nationalId, shaNumber, birthCertificate, accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, false))));
            }
            throw new ResourceNotAuthorizedException("User is not authorized to perform this action");
        }
        return ResponseEntity.ok(insuranceClaimsService.fetchAllClaimsPHC(patientIdType, patientId, claimNumber, patientName, providerName, mflCode, crId, nationalId, shaNumber, birthCertificate, accessCode, level, shaCode, status, auditFlag, shaPackageCode, county, startDate, endDate, PageRequest.of(page, size, Helper.createSortObject(sortBy, direction, false))));
    }


  @PostMapping(Constants.EndPoints.CLAIMS_DATA_API_AUDIT_UPDATE)
  @Override
  public ResponseEntity<String> updateAuditStatus(
          @RequestBody AuditUpdateRequest auditUpdateRequest) {
    log.info("Updating audit status for claim ID: {}", auditUpdateRequest.getClaimId());

    boolean hasPermission =
            permissionService.hasPermission(
                    HealthIdContextHolder.userId(), ResourceEnum.CLAIMS, ActionEnum.EDIT);
    log.info("Role based user Permissions: {}", hasPermission);

    if (!hasPermission) {
      throw new ResourceNotAuthorizedException("User is not authorized to perform this action");
    }
    return ResponseEntity.ok(surveillanceService.updateAuditStatus(auditUpdateRequest));
  }

    @PostMapping(Constants.EndPoints.CLAIMS_DATA_API_SURVEILLANCE_FLAG)
    public ResponseEntity<String> flagClaimForSurveillance(@RequestBody SurveillanceFlagRequest request) {
        log.info("Flagging claim {} for surveillance", request.getClaimId());

        boolean hasPermission = permissionService.hasPermission(HealthIdContextHolder.userId(), ResourceEnum.CLAIMS, ActionEnum.EDIT);
        log.info("Role based user Permissions for surveillance flagging: {}", hasPermission);

        if (!hasPermission) {
            throw new ResourceNotAuthorizedException("User is not authorized to perform this action");
        }

        return ResponseEntity.ok(surveillanceService.flagClaimForSurveillance(request));
    }

    @GetMapping(Constants.EndPoints.CLAIMS_DATA_API_SURVEILLANCE_LOGS)
    @Override
    public ResponseEntity<List<SurveillanceLogResponse>> findSurveillanceLogsByClaimId(@PathVariable String claimId) {
        log.info("Fetching surveillance logs for claim ID: {}", claimId);

        boolean hasPermission = permissionService.hasPermission(HealthIdContextHolder.userId(), ResourceEnum.CLAIMS, ActionEnum.VIEW);
        log.info("Role based user Permissions for surveillance logs: {}", hasPermission);

        if (!hasPermission) {
            throw new ResourceNotAuthorizedException("User is not authorized to perform this action");
        }

        List<SurveillanceLogResponse> logs = surveillanceService.findSurveillanceLogByClaimId(claimId);
        return ResponseEntity.ok(logs);
    }


    @GetMapping(Constants.EndPoints.USER_VERIFY_PERMISSION)
    public ResponseEntity<Boolean> verifyAssignedClaim(@PathVariable String claimId, @PathVariable ResourceEnum resource, @PathVariable ActionEnum action) {
        String username = HealthIdContextHolder.userId();
        log.info("User [verify-claim] Logged In Id: {}", username);
        boolean hasAdminPermission = permissionService.hasPermission(username, ResourceEnum.ADMIN, action);
        if (hasAdminPermission) {
            return ResponseEntity.ok(true);
        }

        boolean hasPermission = permissionService.hasPermission(username, resource, action);
        if (!hasPermission) {
            return ResponseEntity.ok(false);
        }
        return ResponseEntity.ok(insuranceClaimsService.verifyAssignedClaim(claimId, username));
    }
}

