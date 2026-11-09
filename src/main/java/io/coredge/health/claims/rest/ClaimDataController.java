package io.coredge.health.claims.rest;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.dto.AuditUpdateRequest;
import io.coredge.health.claims.dto.SurveillanceLogResponse;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.ClaimHistoryDTO;
import io.coredge.health.claims.models.PaymentDetailResponse;
import io.coredge.health.claims.models.claims.ClaimDataResponse;
import io.coredge.health.claims.models.claims.PatientIdType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

public interface ClaimDataController {

    ResponseEntity<List<ClaimDataResponse>> findByClaimId(@RequestParam String claimId);

    ResponseEntity<CollectionModel<EntityModel<ClaimDataResponse>>> fetchAllPreAuthByPage(@RequestParam(required = false) PatientIdType patientIdType , @RequestParam(required = false)  String patientId ,
                                                                                          @RequestParam(required = false) String claimNumber, @RequestParam(required = false) String patientName,
                                                                                          @RequestParam(required = false) String mflCode, @RequestParam(required = false) String crId,
                                                                                          @RequestParam(required = false) String shaNumber, @RequestParam(required = false) String birthCertificate,
                                                                                          @RequestParam(required = false) String nationalId, @RequestParam(required = false) String accessCode,
                                                                                          @RequestParam(required = false) String providerName, @RequestParam(required = false) String level,
                                                                                          @RequestParam(required = false) String shaCode, @RequestParam(required = false) ExtensionStateCodeEnum status,
                                                                                          @RequestParam(required = false) Boolean auditFlag,
                                                                                          @RequestParam(required = false) String shaPackageCode,
                                                                                          @RequestParam(required = false) String county,
                                                                                          @RequestParam(required = false) boolean filtered,
                                                                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
                                                                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
                                                                                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                                                                          @RequestParam(defaultValue = "claimNumber") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

    ResponseEntity<String> findBYClaimIdFhir(@RequestParam String claimId);

    ResponseEntity<PaymentDetailResponse> findPaymentDetailByClaimId(@PathVariable String claimId);

    ResponseEntity<CollectionModel<EntityModel<ClaimDataResponse>>> fetchAllClaimByPage(@RequestParam(required = false) PatientIdType patientIdType ,@RequestParam(required = false) String patientId ,
                                                                                        @RequestParam(required = false) String claimNumber, @RequestParam(required = false) String patientName,
                                                                                        @RequestParam(required = false) String mflCode, @RequestParam(required = false) String crId,
                                                                                        @RequestParam(required = false) String shaNumber, @RequestParam(required = false) String birthCertificate,
                                                                                        @RequestParam(required = false) String nationalId, @RequestParam(required = false) String accessCode,
                                                                                        @RequestParam(required = false) String providerName, @RequestParam(required = false) String level,
                                                                                        @RequestParam(required = false) String shaCode, @RequestParam(required = false) ExtensionStateCodeEnum status,
                                                                                        @RequestParam(required = false) Boolean auditFlag,
                                                                                        @RequestParam(required = false) String shaPackageCode,
                                                                                        @RequestParam(required = false) String county,
                                                                                        @RequestParam(required = false) boolean filtered,
                                                                                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
                                                                                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
                                                                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                                                                        @RequestParam(defaultValue = "claimNumber") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

    ResponseEntity<List<ClaimHistoryDTO>> fetchClaimLineItems(@PathVariable String claimId);

    ResponseEntity<CollectionModel<EntityModel<ClaimDataResponse>>> fetchAllPaymentMechanismClaim(@RequestParam(required = false) PatientIdType patientIdType ,@RequestParam(required = false) String patientId ,
                                                                                                  @RequestParam(required = false) String claimNumber, @RequestParam(required = false) String patientName,
                                                                                                  @RequestParam(required = false) String mflCode, @RequestParam(required = false) String crId,
                                                                                                  @RequestParam(required = false) String shaNumber, @RequestParam(required = false) String birthCertificate,
                                                                                                  @RequestParam(required = false) String nationalId, @RequestParam(required = false) String accessCode,
                                                                                                  @RequestParam(required = false) String providerName, @RequestParam(required = false) String level,
                                                                                                  @RequestParam(required = false) String shaCode, @RequestParam(required = false) ExtensionStateCodeEnum status,
                                                                                                  @RequestParam(required = false) Boolean auditFlag,
                                                                                                  @RequestParam(required = false) String shaPackageCode,
                                                                                                  @RequestParam(required = false) String county,
                                                                                                  @RequestParam(required = false) boolean filtered,
                                                                                                  @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
                                                                                                  @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
                                                                                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                                                                                  @RequestParam(defaultValue = "claimNumber") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

    @GetMapping(Constants.EndPoints.CLAIMS_DATA_API_TNT_ALL_PAGE_CLAIM)
    ResponseEntity<CollectionModel<EntityModel<ClaimDataResponse>>>
    fetchAllClaimByPageTnt(PatientIdType patientIdType, String patientId, String claimNumber, String patientName, String mflCode, String crId, String shaNumber, String birthCertificate, String nationalId, String accessCode, String providerName, String level, String shaCode, ExtensionStateCodeEnum status, Boolean auditFlag, String shaPackageCode, String county, boolean filtered, LocalDateTime startDate, LocalDateTime endDate, int page, int size, String sortBy, String direction);

    ResponseEntity<CollectionModel<EntityModel<ClaimDataResponse>>> fetchAllUpdatedClaimByPage(@RequestParam(required = false) PatientIdType patientIdType , @RequestParam(required = false) String patientId ,
                                                                                               @RequestParam(required = false) String claimNumber, @RequestParam(required = false) String patientName,
                                                                                               @RequestParam(required = false) String providerName, @RequestParam(required = false) String crId,
                                                                                               @RequestParam(required = false) String nationalId, @RequestParam(required = false) String shaNumber,
                                                                                               @RequestParam(required = false) String accessCode, @RequestParam(required = false) String fidCode,
                                                                                               @RequestParam(required = false) String level, @RequestParam(required = false) String shaCode,
                                                                                               @RequestParam(required = false) String shaPackageCode, @RequestParam(required = false) String county,
                                                                                               @RequestParam(required = false) ExtensionStateCodeEnum status,
                                                                                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
                                                                                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
                                                                                               @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
                                                                                               @RequestParam(defaultValue = "claimNumber") String sortBy, @RequestParam(defaultValue = "ASC") String direction);
    ResponseEntity<String> updateAuditStatus(@RequestBody AuditUpdateRequest auditUpdateRequest);

    ResponseEntity<List<SurveillanceLogResponse>> findSurveillanceLogsByClaimId(@PathVariable String claimId);
}



