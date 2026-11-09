package io.coredge.health.claims.utils;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.eclaims.ClaimPreAuthMappingProjection;
import io.coredge.health.claims.models.eclaims.ClaimProjection;
import io.coredge.health.claims.models.eclaims.PreAuthProjection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.APPROVED_EXTENSION_STATES;
import static io.coredge.health.claims.constants.Constants.CLAIM_APPROVED_EXTENSION_STATES;

@Slf4j
@Service
public class PreAuthLimits {

    @Autowired
    private InsuranceClaimsRepository claimsRepository;

    @Autowired
    private LineItemRepository lineItemRepository;

    /**
     * Counts the number of valid or consumed pre-auth claims based on the provided criteria.
     *
     * @param crId    The cr ID to filter claims.
     * @param status  The list of approved status to filter claims.
     * @param shaCode The SHA code to filter claims.
     * @param use     The use type of the claim (PREAUTHORIZATION).
     * @param excludeClaimId The claim ID to exclude from counting (to prevent self-counting).
     * @return The count of valid or consumed pre-auth claims.
     */
    public long countValidOrConsumedPreAuthClaimsOnCrId(String crId, List<ExtensionStateCodeEnum> status, List<String> shaCode,
                                                        String use, LocalDate policyStartDate, LocalDate policyEndDate, UUID excludeClaimId) {
        // Step 1: Fetch all pre-auth claims for the patient with specified criteria (excludeClaimId filtered in repository)
        log.info("countValidOrConsumedPreAuthClaimsOnCrId : shared for Query : CrId:{} , Status:{} , ShaCode:{} , USe:{} , PolicyStartDate:{} , PolicyEndDate:{} , ExcludeClaimId:{}" , crId , status , shaCode , use , policyStartDate , policyEndDate, excludeClaimId);
        List<PreAuthProjection> preAuthClaims = claimsRepository.findAllPreAuthProjectedOnCrId(crId, status, shaCode, Constants.Use.PREAUTHORIZATION, excludeClaimId, policyStartDate, policyEndDate);

        // Step 2: Fetch all claims for the patient to identify consumed pre-auths
        log.info("countValidOrConsumedPreAuthClaimsOnCrId : shared for Query : CrId:{} ,Use :{} , Status:{} , Shacode:{}  , PolicyStartDate:{} , PolicyEndDate:{}" , crId ,Constants.Use.CLAIM, CLAIM_APPROVED_EXTENSION_STATES ,  shaCode ,policyStartDate , policyEndDate);
        List<ClaimPreAuthMappingProjection> allClaims = claimsRepository.findAllClaimProjectedOnCrId(crId, shaCode, Constants.Use.CLAIM, CLAIM_APPROVED_EXTENSION_STATES, policyStartDate, policyEndDate);

        // Step 3: Extract all pre-auth IDs that are directly consumed in claims
        Set<UUID> consumedPreAuthIds = allClaims.stream()
                .map(ClaimPreAuthMappingProjection::getPreAuthId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 4: Identify non-consumed but valid pre-auths
//        LocalDate today = DateUtils.nowNairobiLocalDate();

        long nonConsumedButValidPreAuthCount = preAuthClaims.stream()
                .filter(preAuth -> !consumedPreAuthIds.contains(preAuth.getClaimId())) // Not consumed
//                .filter(preAuth -> preAuth.getExpiryDate() != null && !preAuth.getExpiryDate().isBefore(today))
                .count();

        log.info("CR ID: {} - Non-consumed but valid PreAuth count: {}", crId, nonConsumedButValidPreAuthCount);

        // Step 5: Total = all claims count + Non-consumed pre-auths
        return allClaims.size() + nonConsumedButValidPreAuthCount;
    }

    /**
     * Counts the number of valid or consumed pre-auth claims based on the provided criteria.
     *
     * @param patientHouseholdNo The Household No to filter claims.
     * @param status             The list of approved status to filter claims.
     * @param shaCode            The SHA code to filter claims.
     * @param use                The use type of the claim (PREAUTHORIZATION).
     * @param excludeClaimId     The claim ID to exclude from counting (to prevent self-counting).
     * @return The count of valid or consumed pre-auth claims.
     */
    public long countValidOrConsumedPreAuthClaimsOnHouseHold(String patientHouseholdNo, List<ExtensionStateCodeEnum> status,
                                                             List<String> shaCode, String use, LocalDate policyStartDate, LocalDate policyEndDate, UUID excludeClaimId) {
        // Step 1: Fetch all pre-auth claims for the patient with specified criteria (excludeClaimId filtered in repository)
        log.info("countValidOrConsumedPreAuthClaimsOnHouseHold : shared for Query : HouseHold:{} , Status:{} , ShaCode:{} , USe:{} , PolicyStartDate:{} , PolicyEndDate:{} , ExcludeClaimId:{}" , patientHouseholdNo , status , shaCode , use , policyStartDate , policyEndDate, excludeClaimId);
        List<PreAuthProjection> preAuthClaims = claimsRepository.findAllPreAuthProjectedOnHouseHold(patientHouseholdNo, status, shaCode, use, excludeClaimId, policyStartDate, policyEndDate);

        // Step 2: Fetch all claims for the patient to identify consumed pre-auths
        log.info("countValidOrConsumedPreAuthClaimsOnHouseHold : shared for Query : HouseHold:{} , ShaCode:{} , USe:{} , Status : {} , PolicyStartDate:{} , PolicyEndDate:{}" , patientHouseholdNo  , shaCode  ,Constants.Use.CLAIM, CLAIM_APPROVED_EXTENSION_STATES, policyStartDate , policyEndDate);
        List<ClaimPreAuthMappingProjection> allClaims = claimsRepository.findAllClaimProjectedOnHouseHoldNo(patientHouseholdNo, shaCode, Constants.Use.CLAIM, CLAIM_APPROVED_EXTENSION_STATES, policyStartDate, policyEndDate);

        // Step 3: Extract all pre-auth IDs that are directly consumed in claims
        Set<UUID> consumedPreAuthIds = allClaims.stream()
                .map(ClaimPreAuthMappingProjection::getPreAuthId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 4: Identify non-consumed but valid pre-auths
//        LocalDate today = DateUtils.nowNairobiLocalDate();

        long nonConsumedButValidPreAuthCount = preAuthClaims.stream()
                .filter(preAuth -> !consumedPreAuthIds.contains(preAuth.getClaimId())) // Not consumed
//                .filter(preAuth -> preAuth.getExpiryDate() != null && !preAuth.getExpiryDate().isBefore(today))
                .count();

        log.info("Household No: {} - Non-consumed but valid PreAuth count: {}", patientHouseholdNo, nonConsumedButValidPreAuthCount);

        // Step 5: Total = all claims count + Non-consumed and Non expired pre-auths
        return allClaims.size() + nonConsumedButValidPreAuthCount;
    }

    /**
     * Calculates the total approved amount for pre-auth claims, considering expiry and consumption checks.
     *
     * @param crId        The household number to filter claims.
     * @param status      The list of approved status to filter claims.
     * @param packageCode The SHA package to filter claims.
     * @param use         The use type of the claim (PREAUTHORIZATION).
     * @param excludeClaimId The claim ID to exclude from counting (to prevent self-counting).
     * @return The total approved amount for valid or consumed pre-auth claims.
     */
    public BigDecimal getTotalApprovedAmountWithExpiryAndConsumptionCheck(String crId, List<ExtensionStateCodeEnum> status, String packageCode,
                                                                          String use, LocalDate policyStartDate, LocalDate policyEndDate, UUID excludeClaimId) {
        // Step 1: Fetch all pre-auth claims for the patient with specified criteria (excludeClaimId filtered in repository)
        log.info("getTotalApprovedAmountWithExpiryAndConsumptionCheck : shared for Query : CrId:{} , Status:{} , Package:{} , USe:{} , PolicyStartDate:{} , PolicyEndDate:{} , ExcludeClaimId:{}" , crId , status , packageCode , use , policyStartDate , policyEndDate, excludeClaimId);
        List<PreAuthProjection> preAuthClaims = claimsRepository.findAllPreAuthProjectedByPackage(crId, status, packageCode, use, excludeClaimId, policyStartDate, policyEndDate);
        log.info("Patient ID: {} - No. of preAuth items : {}", crId, preAuthClaims.size());

        // Step 2: Fetch all claims for the patient to identify consumed pre-auths
        log.info("getTotalApprovedAmountWithExpiryAndConsumptionCheck : shared for Query : CrId:{} , Status:{} , Package:{} , PolicyStartDate:{} , PolicyEndDate:{} " , crId ,CLAIM_APPROVED_EXTENSION_STATES , packageCode , policyStartDate , policyEndDate );
        List<ClaimProjection> allClaims = claimsRepository.findAllClaimProjectedByPackage(crId, CLAIM_APPROVED_EXTENSION_STATES, packageCode, Constants.Use.CLAIM, policyStartDate, policyEndDate);
        log.info("Patient ID: {} - No. of Claims items : {}", crId, allClaims.size());

        // Step 3: Extract all pre-auth IDs that are directly consumed in claims
        Set<UUID> consumedPreAuthIds = allClaims.stream()
                .map(ClaimProjection::getPreAuthId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 4: Identify non-consumed but valid pre-auths
//        LocalDate today = DateUtils.nowNairobiLocalDate();

        // Step 5: Get the Sum of pre-auths that are not consumed.
        BigDecimal nonConsumedButValidPreAuthSum = preAuthClaims.stream()
                .filter(preAuth -> !consumedPreAuthIds.contains(preAuth.getClaimId())) // Not consumed
//                .filter(preAuth -> preAuth.getExpiryDate() != null && !preAuth.getExpiryDate().isBefore(today))
                .map(claim -> claim.getApprovedPrice() != null ? claim.getApprovedPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("Patient ID: {} - PreAuth non-consumed sum : {}", crId, nonConsumedButValidPreAuthSum);

        // Step 6: Sum of all claims approvedPrice.
    /*    BigDecimal allClaimsSum = allClaims.stream()
                .map(ClaimProjection::getApprovedPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("Patient ID: {} - All Claims sum : {}", crId, allClaimsSum);*/


        // Step 6: Sum of all claims approvedPrice, handling nulls.
        BigDecimal allClaimsSum = allClaims.stream()
                .map(claim -> claim.getApprovedPrice() != null ? claim.getApprovedPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Patient ID: {} - All Claims sum : {}", crId, allClaimsSum);

        // Step 6: Total Sum of all claims approvedPrice and valid preAuths.
        BigDecimal totalSum = allClaimsSum.add(nonConsumedButValidPreAuthSum);
        log.info("Patient ID: {} - Total approved amount with expiry and consumption check: {}", crId, totalSum);
        return totalSum;
    }

    /**
     * Calculates the total Approved ECCIF amount for pre-auth claims, considering expiry and consumption checks.
     *
     * @param crId        The cr ID to filter claims.
     * @param status      The list of approved status to filter claims.
     * @param packageCode The SHA package to filter claims.
     * @param use         The use type of the claim (PREAUTHORIZATION).
     * @param excludeClaimId The claim ID to exclude from counting (to prevent self-counting).
     * @return The total approved amount for valid or consumed pre-auth claims.
     */
    public BigDecimal getTotalApprovedEccifAmountWithExpiryAndConsumptionCheck(String crId, List<ExtensionStateCodeEnum> status, String packageCode,
                                                                               String use, LocalDate policyStartDate, LocalDate policyEndDate, UUID excludeClaimId) {

        // Step 1: Fetch all pre-auth claims for the patient with specified criteria (excludeClaimId filtered in repository)
        log.info("getTotalApprovedEccifAmountWithExpiryAndConsumptionCheck : shared for Query : CrId:{} , Status:{} , Package:{} ,USe {} , PolicyStartDate:{} , PolicyEndDate:{} , ExcludeClaimId:{}" , crId ,status , packageCode ,use, policyStartDate , policyEndDate, excludeClaimId);
        List<PreAuthProjection> preAuthClaims = claimsRepository.findAllPreAuthEccifAmountByPackage(crId, status, packageCode, use, excludeClaimId, policyStartDate, policyEndDate);

        // Step 2: Fetch all claims for the patient to identify consumed pre-auths
        log.info("getTotalApprovedEccifAmountWithExpiryAndConsumptionCheck : shared for Query : CrId:{} , Status:{} , Package:{} ,Use:{} , PolicyStartDate:{} , PolicyEndDate:{} " , crId ,CLAIM_APPROVED_EXTENSION_STATES , packageCode ,Constants.Use.CLAIM, policyStartDate , policyEndDate );
        List<ClaimProjection> allClaims = claimsRepository.findAllClaimEccifAmountByPackage(crId, CLAIM_APPROVED_EXTENSION_STATES, packageCode, Constants.Use.CLAIM, policyStartDate, policyEndDate);

        // Step 3: Extract all pre-auth IDs that are directly consumed in claims
        Set<UUID> consumedPreAuthIds = allClaims.stream()
                .map(ClaimProjection::getPreAuthId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 4: Check expiry or consumption
//        LocalDate today = DateUtils.nowNairobiLocalDate();

        // Step 5: Get the Sum of pre-auths that are not consumed and not expired.
        BigDecimal nonConsumedButValidPreAuthSum = preAuthClaims.stream()
                .filter(preAuth -> !consumedPreAuthIds.contains(preAuth.getClaimId())) // Not consumed
//                .filter(preAuth -> preAuth.getExpiryDate() != null && !preAuth.getExpiryDate().isBefore(today))
                .map(PreAuthProjection::getApprovedPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("Patient ID: {} - PreAuth non-consumed sum : {}", crId, nonConsumedButValidPreAuthSum);

        // Step 5: Sum of all claims approvedPrice.
        BigDecimal allClaimsSum = allClaims.stream()
                .map(ClaimProjection::getApprovedPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("Patient ID: {} - All Claims sum : {}", crId, allClaimsSum);

        // Step 6: Total Sum of all claims approvedPrice and valid preAuths.
        BigDecimal totalSum = allClaimsSum.add(nonConsumedButValidPreAuthSum);
        log.info("Patient ID: {} - Total approved amount with expiry and consumption check: {}", crId, totalSum);
        return totalSum;
    }

    /**
     * Counts the number of valid or consumed claims at level 3 based on the provided criteria.
     *
     * @param householdNumber The household number to filter claims.
     * @param interventionCodes The intervention codes to filter claims.
     * @param use The use type of the claim (PREAUTHORIZATION).
     * @param approvedStatuses The list of approved statuses to filter claims.
     * @param excludeClaimId The claim ID to exclude from counting (to prevent self-counting).
     * @return The count of valid or consumed claims at level 3.
     */
    public long countValidOrConsumedClaimsAtLevel3(String householdNumber, List<String> interventionCodes, String use,
                                                   List<String> approvedStatuses, UUID excludeClaimId) {
        // Step 1: Fetch projected claims (excludeClaimId filtered in repository)
        List<PreAuthProjection> preAuthClaims = claimsRepository.findUsedMinorSurgeryClaimProjectionsAtLevel3(householdNumber, interventionCodes, use, approvedStatuses, excludeClaimId, null, null);

        // Step 2: Fetch consumed claims (e.g., used in CLAIM)
        List<ClaimPreAuthMappingProjection> allClaims = claimsRepository.findAllUsedMinorSurgeryClaimAtLevel3(householdNumber, interventionCodes, Constants.Use.CLAIM, approvedStatuses, null, null);

        // Step 3: Extract all pre-auth IDs that are directly consumed in claims
        Set<UUID> consumedPreAuthIds = allClaims.stream()
                .map(ClaimPreAuthMappingProjection::getPreAuthId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 4: Identify non-consumed but valid pre-auths
        long nonConsumedButValidPreAuthCount = preAuthClaims.stream()
                .filter(preAuth -> !consumedPreAuthIds.contains(preAuth.getClaimId())) // Not consumed
                .count();

        log.info("Household No: {} - Non-consumed but valid PreAuth count: {}", householdNumber, nonConsumedButValidPreAuthCount);

        // Step 5: Total = all claims count + Non-consumed pre-auths
        return allClaims.size() + nonConsumedButValidPreAuthCount;
    }

    /**
     * Calculates the total approved shif amount for pre-auth claims, considering consumption checks.
     *
     * @param crId        The household number to filter claims.
     * @param status      The list of approved status to filter claims.
     * @param packageCode The SHA package to filter claims.
     * @param use         The use type of the claim (PREAUTHORIZATION).
     * @param excludeClaimId The claim ID to exclude from counting (to prevent self-counting).
     * @return The total approved amount for valid or consumed pre-auth claims.
     */
    public BigDecimal getTotalApprovedShifAmountWithConsumptionCheck(String crId, List<ExtensionStateCodeEnum> status, String packageCode,
                                                                          String use, LocalDate policyStartDate, LocalDate policyEndDate, UUID excludeClaimId) {
        // Step 1: Fetch all pre-auth claims for the patient with specified criteria (excludeClaimId filtered in repository)
        log.info("getTotalApprovedShifAmountWithConsumptionCheck : shared for Query : CrId:{} , Status:{} , Package:{} , USe:{} , PolicyStartDate:{} , PolicyEndDate:{} , ExcludeClaimId:{}" , crId , status , packageCode , use , policyStartDate , policyEndDate, excludeClaimId);
        List<PreAuthProjection> preAuthClaims = claimsRepository.findAllShifPreAuthProjectedByPackage(crId, status, packageCode, use, excludeClaimId, policyStartDate, policyEndDate);
        log.info("Patient ID: {} - No. of preAuth items : {}", crId, preAuthClaims.size());

        // Step 2: Fetch all claims for the patient to identify consumed pre-auths
        log.info("getTotalApprovedShifAmountWithConsumptionCheck : shared for Query : CrId:{} , Status:{} , Package:{} , PolicyStartDate:{} , PolicyEndDate:{} " , crId ,CLAIM_APPROVED_EXTENSION_STATES , packageCode , policyStartDate , policyEndDate );
        List<ClaimProjection> allClaims = claimsRepository.findAllShifClaimProjectedByPackage(crId, CLAIM_APPROVED_EXTENSION_STATES, packageCode, Constants.Use.CLAIM, policyStartDate, policyEndDate);
        log.info("Patient ID: {} - No. of Claims items : {}", crId, allClaims.size());

        // Step 3: Extract all pre-auth IDs that are directly consumed in claims
        Set<UUID> consumedPreAuthIds = allClaims.stream()
                .map(ClaimProjection::getPreAuthId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());


        // Step 4: Get the Sum of pre-auths that are not consumed.
        BigDecimal nonConsumedButValidPreAuthSum = preAuthClaims.stream()
                .filter(preAuth -> !consumedPreAuthIds.contains(preAuth.getClaimId())) // Not consumed
                .map(claim -> claim.getApprovedPrice() != null ? claim.getApprovedPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("Patient ID: {} - PreAuth non-consumed sum : {}", crId, nonConsumedButValidPreAuthSum);


        // Step 5: Sum of all claims approvedPrice, handling nulls.
        BigDecimal allClaimsSum = allClaims.stream()
                .map(claim -> claim.getApprovedPrice() != null ? claim.getApprovedPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Patient ID: {} - All Claims sum : {}", crId, allClaimsSum);

        // Step 6: Total Sum of all claims approvedPrice and valid preAuths.
        BigDecimal totalSum = allClaimsSum.add(nonConsumedButValidPreAuthSum);
        log.info("Patient ID: {} - Total approved amount with expiry and consumption check: {}", crId, totalSum);
        return totalSum;
    }

    /**
     * Calculates the total approved amount for pre-auth claims, considering expiry and consumption checks.
     *
     * @param crId        The household number to filter claims.
     * @param status      The list of approved status to filter claims.
     * @param interventionCodes The SHA codes to filter claims.
     * @param use         The use type of the claim (PREAUTHORIZATION).
     * @param excludeClaimId The claim ID to exclude from counting (to prevent self-counting).
     * @return The total approved amount for valid or consumed pre-auth claims.
     */
    public BigDecimal getTotalApprovedAmountWithExpiryAndConsumptionCheck(String crId, List<ExtensionStateCodeEnum> status, List<String> interventionCodes,
                                                                          String use, LocalDate policyStartDate, LocalDate policyEndDate, UUID excludeClaimId) {
        // Step 1: Fetch all pre-auth claims for the patient with specified criteria (excludeClaimId filtered in repository)
        List<PreAuthProjection> preAuthClaims = claimsRepository.findAllPreAuthProjectedByCodes(crId, status, interventionCodes, use, excludeClaimId, policyStartDate, policyEndDate);
        log.info("Patient ID: {} - No. of preAuth items : {}", crId, preAuthClaims.size());

        // Step 2: Fetch all claims for the patient to identify consumed pre-auths
        List<ClaimProjection> allClaims = claimsRepository.findAllClaimProjectedByCodes(crId, CLAIM_APPROVED_EXTENSION_STATES, interventionCodes, Constants.Use.CLAIM, policyStartDate, policyEndDate);
        log.info("Patient ID: {} - No. of Claims items : {}", crId, allClaims.size());

        // Step 3: Extract all pre-auth IDs that are directly consumed in claims
        Set<UUID> consumedPreAuthIds = allClaims.stream()
                .map(ClaimProjection::getPreAuthId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Step 4: Get the Sum of pre-auths that are not consumed.
        BigDecimal nonConsumedButValidPreAuthSum = preAuthClaims.stream()
                .filter(preAuth -> !consumedPreAuthIds.contains(preAuth.getClaimId())) // Not consumed
                .map(claim -> claim.getApprovedPrice() != null ? claim.getApprovedPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("Patient ID: {} - PreAuth non-consumed sum : {}", crId, nonConsumedButValidPreAuthSum);

        // Step 5: Sum of all claims approvedPrice, handling nulls.
        BigDecimal allClaimsSum = allClaims.stream()
                .map(claim -> claim.getApprovedPrice() != null ? claim.getApprovedPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Patient ID: {} - All Claims sum : {}", crId, allClaimsSum);

        // Step 6: Total Sum of all claims approvedPrice and valid preAuths.
        BigDecimal totalSum = allClaimsSum.add(nonConsumedButValidPreAuthSum);
        log.info("Patient ID: {} - Total approved amount with expiry and consumption check: {}", crId, totalSum);
        return totalSum;
    }

}
