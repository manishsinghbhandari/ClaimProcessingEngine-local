/**
 * Author: Reinhard Odhiambo
 * 
 * Service for processing scheme member limits and claim transactions.
 * Handles deduction and reversal of member limits based on claim status and benefit.
 */

package io.coredge.health.claims.service.BMS;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.coredge.health.claims.exception.app.ClaimTransactionException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.coredge.health.claims.db.entity.BMS.Benefit;
import io.coredge.health.claims.db.entity.BMS.ClaimStatus;
import io.coredge.health.claims.db.entity.BMS.ClaimTransaction;
import io.coredge.health.claims.db.entity.BMS.SchemeBenefitLimit;
import io.coredge.health.claims.db.entity.BMS.SchemeDetail;
import io.coredge.health.claims.db.entity.BMS.SchemeMember;
import io.coredge.health.claims.db.entity.BMS.SchemeMemberLimit;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.BMS.BenefitRepository;
import io.coredge.health.claims.db.repositories.BMS.SchemeDetailRepository;
import io.coredge.health.claims.db.repositories.BMS.SchemeMemberLimitRepository;
import io.coredge.health.claims.db.repositories.BMS.SchemeMemberRepository;
import io.coredge.health.claims.db.repositories.BMS.ClaimStatusRepository;
import io.coredge.health.claims.db.repositories.BMS.ClaimTransactionRepository;
import io.coredge.health.claims.db.repositories.BMS.SchemeBenefitLimitRepository;
import lombok.extern.slf4j.Slf4j;
import io.coredge.health.claims.constants.Constants.BMSStatuses;
import io.coredge.health.claims.constants.Constants.ClaimStatuses;
import io.coredge.health.claims.constants.Constants.ClaimTransactionOperation;
import io.coredge.health.claims.db.entity.InsuranceClaim;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchemeMemberLimitService {

    // Repository dependencies injected via constructor
    private final SchemeMemberLimitRepository schemeMemberLimitRepository;
    private final InsuranceClaimsRepository insuranceClaimsRepository;
    private final BenefitRepository benefitRepository;
    private final SchemeDetailRepository schemeDetailRepository;
    private final SchemeMemberRepository schemeMemberRepository;
    private final ClaimStatusRepository claimStatusRepository;
    private final ClaimTransactionRepository claimTransactionRepository;
    private final SchemeBenefitLimitRepository schemeBenefitLimitRepository;

    @Value("${bms.enabled:false}")
    private boolean bmsEnabled;

    /**
     * Processes a claim by deducting or reversing the member's benefit limit based on claim status.
     * 
     * @param claimID     The unique identifier of the claim.
     * @param benefitCode The code of the benefit being claimed.
     * @param amount      The amount to process.
     * @param schemeCode  The code of the scheme.
     * @return The resulting ClaimTransaction.
     */
    public ClaimTransaction processClaim(UUID claimID, String benefitCode, Double amount, String schemeCode) {
        if (!bmsEnabled) {
            log.warn("BMS processing is disabled. Skipping claim processing for claimID: {}", claimID);
            return null;
        }
        log.info("Starting processClaim for claimID: {}, benefitCode: {}, amount: {}, schemeCode: {}", claimID, benefitCode, amount, schemeCode);

        // Check if claim exists and retrieve it
        Optional<List<InsuranceClaim>> claimOptional = insuranceClaimsRepository.findByClaimId(claimID);
        if (claimOptional.get().isEmpty()) {
            log.warn("Claim not found for ID: {}", claimID);
            throw new ClaimTransactionException("Claim with ID " + claimID + " does not exist.");
        }
        InsuranceClaim claim = claimOptional.get().getFirst();
        log.info("Found claim: {}", claim.getClaimId().toString());

        // Check if benefit exists and retrieve it
        Optional<Benefit> benefitOptional = benefitRepository.findByCode(benefitCode);
        if (benefitOptional.isEmpty()) {
            log.warn("Benefit not found for code: {}", benefitCode);
            throw new ClaimTransactionException("Benefit with code " + benefitCode + " does not exist.");
        }
        Benefit benefit = benefitOptional.get();
        log.info("Found benefit: {}", benefit.getId());

        // Check if scheme exists and retrieve it
        Optional<SchemeDetail> schemeOptional = schemeDetailRepository.findBySystemCode(schemeCode);
        if (schemeOptional.isEmpty()) {
            log.warn("Scheme not found for code: {}", schemeCode);
            throw new ClaimTransactionException("Scheme with code " + schemeCode + " does not exist.");
        }
        SchemeDetail scheme = schemeOptional.get();
        log.info("Found scheme: {}", scheme.getId());

        // Check if scheme member exists and retrieve it
        Optional<SchemeMember> schemeMemberOptional = schemeMemberRepository.findByPatientIdAndSchemeDetail(
                claim.getPatient().getId().toString(), scheme);
        if (schemeMemberOptional.isEmpty()) {
            log.warn("Scheme member not found for patient: {} and scheme: {}", claim.getPatient().getId().toString(), schemeCode);
            throw new ClaimTransactionException("Scheme member for patient " + claim.getPatient().getId().toString() + " and scheme " + schemeCode + " does not exist.");
        }
        SchemeMember schemeMember = schemeMemberOptional.get();
        log.info("Found scheme member: {}", schemeMember.getId());

        // Get Scheme Benefit Limit for scheme member, benefit, and scheme detail
        Optional<SchemeBenefitLimit> schemeBenefitLimitOptional = schemeBenefitLimitRepository.findBySchemeDetailAndBenefitAndJobGroup(
                scheme, benefit, schemeMember.getJobGroup());
        if (schemeBenefitLimitOptional.isEmpty()) {
            log.warn("Scheme benefit limit not found for scheme: {}, benefit: {}, job group: {}", schemeCode, benefitCode, schemeMember.getJobGroup().getId());
            throw new ClaimTransactionException("Scheme benefit limit for scheme " + schemeCode + ", benefit " + benefitCode + ", and job group " + schemeMember.getJobGroup().getId() + " does not exist.");
        }
        SchemeBenefitLimit schemeBenefitLimit = schemeBenefitLimitOptional.get();

        SchemeMemberLimit limit = null;
        // Check if scheme limit is shared; if so, deduct from principal member's limit
        if (schemeBenefitLimit.getIsLimitShared() != null && schemeBenefitLimit.getIsLimitShared()) {
            log.info("Scheme benefit limit is shared. Fetching principal member limit.");
            // Get principal member
            Optional<SchemeMember> principleMemberOptional = schemeMemberRepository.findByMemberNumberAndSchemeDetail(
                    schemeMember.getPrincipalMemberNumber(), scheme);
            if (principleMemberOptional.isEmpty()) {
                log.warn("Principal member not found for member number: {} and scheme: {}", schemeMember.getPrincipalMemberNumber(), schemeCode);
                throw new ClaimTransactionException("Principal member for member number " + schemeMember.getPrincipalMemberNumber() + " and scheme " + schemeCode + " does not exist.");
            }
            SchemeMember principleMember = principleMemberOptional.get();
            // Get the principal member limit for the scheme member
            Optional<SchemeMemberLimit> principleMemberLimitOptional = schemeMemberLimitRepository.findBySchemeMemberAndBenefitAndSchemeDetail(
                    principleMember, benefit, scheme);
            if (principleMemberLimitOptional.isEmpty()) {
                log.warn("Principal member limit not found for scheme member: {}, benefit: {}, scheme: {}", schemeMember.getPrincipalMemberNumber(), benefitCode, schemeCode);
                throw new ClaimTransactionException("Principal member limit for scheme member " + schemeMember.getPrincipalMemberNumber() + ", benefit " + benefitCode + ", and scheme " + schemeCode + " does not exist.");
            }
            limit = principleMemberLimitOptional.get();
            log.info("Found principal member limit: {}", limit.getId());
        } else {
            // Check if limit exists for scheme member, benefit, and scheme detail
            Optional<SchemeMemberLimit> limitOptional = schemeMemberLimitRepository.findBySchemeMemberAndBenefitAndSchemeDetail(
                    schemeMember, benefit, scheme);
            if (limitOptional.isEmpty()) {
                log.warn("Scheme member limit not found for scheme member: {}, benefit: {}, scheme: {}", schemeMember.getId(), benefitCode, schemeCode);
                throw new ClaimTransactionException("Scheme member limit for scheme member " + schemeMember.getId() + ", benefit " + benefitCode + ", and scheme " + schemeCode + " does not exist.");
            }
            limit = limitOptional.get();
            log.info("Found scheme member limit: {}", limit.getId());
        }

        // Get claim status and corresponding operation
        String status = claim.getStatus().toString();
        String claimStatusCode = getClaimStatusCode(status);

        log.info("Claim status code: {}", claimStatusCode);
        Optional<ClaimStatus> claimStatusOptional = claimStatusRepository.findByAutoCode(claimStatusCode);
        if (claimStatusOptional.isEmpty()) {
            log.warn("Claim status not found for code: {}", status);
            throw new ClaimTransactionException("Claim status with code " + status + " does not exist.");
        }
        ClaimStatus claimStatus = claimStatusOptional.get();
        log.info("Found claim status: {}, operation: {}", claimStatus.getId(), claimStatus.getOperation());

        // Check if claim transaction exists for this claim
        Optional<ClaimTransaction> claimTransactionOptional = claimTransactionRepository.findTopByClaim_ClaimIdOrderByCreatedAtDesc(claimID);
        ClaimTransaction claimTransaction;
        if (!claimTransactionOptional.isPresent()) {
            // If no previous claim transaction exists, create new
            return newClaimTransaction(amount, claimStatus, limit, claim, benefit);
        } else {
            // If previous claim transaction exists, perform reversal
            return newClaimTransactionReversal(claimTransactionOptional.get(), limit, claim, benefit, claimStatus);
        }
    }

    private ClaimTransaction newClaimTransactionReversal(ClaimTransaction claimTransaction , SchemeMemberLimit limit, InsuranceClaim claim, Benefit benefit, ClaimStatus claimStatus) {
        log.info("Found previous claim transaction. Reversing operation: {}", claimTransaction.getOperation());
        String operation = claimTransaction.getOperation();
        double claimAmount = claimTransaction.getAmount();
        double currentBalance = limit.getLimitBalance();

        if (ClaimTransactionOperation.DEBIT.equalsIgnoreCase(operation)) {
            // Reverse deduction by adding back to balance
            log.info("Reversing deduction. Adding back to balance.");
            limit.setLimitBalance(currentBalance + claimAmount);
            double utilized = limit.getLimitUtilized() - claimAmount;
            if (utilized < 0) {
                double overUtilization = limit.getLimitOverUtilized() + utilized;
                log.info("Utilized went negative. Adjusting overutilization. Before: {}", limit.getLimitOverUtilized());
                limit.setLimitUtilized(0.0);
                limit.setLimitOverUtilized(Math.max(0.0, overUtilization));
                log.info("After adjustment, overutilization: {}", limit.getLimitOverUtilized());
            } else {
                limit.setLimitUtilized(utilized);
            }
            log.info("After reversal, limit balance: {}, utilized: {}", limit.getLimitBalance(), limit.getLimitUtilized());
        } else if (ClaimTransactionOperation.CREDIT.equalsIgnoreCase(operation)) {
            log.info("Reversing credit. Subtracting from balance.");
            if (limit.getLimitOverUtilized() > 0) {
                double overUtilization = limit.getLimitOverUtilized();
                log.info("Increasing overutilization by claim amount.");
                limit.setLimitOverUtilized(overUtilization + claimAmount);
            } else {
                limit.setLimitUtilized(limit.getLimitUtilized() + claimAmount);
            }
            limit.setLimitBalance(currentBalance - claimAmount);
            log.info("After reversal, limit balance: {}, utilized: {}, overutilization: {}", limit.getLimitBalance(), limit.getLimitUtilized(), limit.getLimitOverUtilized());
        } else {
            log.warn("Invalid operation: {}", operation);
            throw new ClaimTransactionException("Invalid operation: " + operation);
        }
        schemeMemberLimitRepository.save(limit);
        // Create a new claim transaction for the reversal
        ClaimTransaction reversalTransaction = getClaimTransactionReversal(claim, benefit, claimAmount, claimStatus, operation, limit);

        log.info("Saved reversal claim transaction with ID: {}", reversalTransaction.getId());
        return reversalTransaction;
    }

    private ClaimTransaction newClaimTransaction(Double amount, ClaimStatus claimStatus, SchemeMemberLimit limit, InsuranceClaim claim, Benefit benefit) {
        ClaimTransaction claimTransaction;
        log.info("No previous claim transaction found. Creating new transaction.");
        // Perform operation on member limit based on claim status operation
        String operation = claimStatus.getOperation();
        double claimAmount = amount;
        double currentBalance = limit.getLimitBalance();

        log.info("Processing operation: {} for new claim transaction.", operation);

        if (ClaimTransactionOperation.DEBIT.equalsIgnoreCase(operation)) {
            // Deduct amount from limit balance
            debitClaimTransaction(currentBalance, limit, claimAmount);
        } else if (ClaimTransactionOperation.CREDIT.equalsIgnoreCase(operation)) {
            // Add amount to limit balance
            creditClaimTransaction(currentBalance, limit, claimAmount);
        } else {
            log.warn("Invalid operation: {}", operation);
            throw new ClaimTransactionException("Invalid operation: " + operation);
        }
        // If claim transaction does not exist, create a new one
        claimTransaction = getClaimTransaction(amount, claim, benefit, claimStatus, operation, limit);
        schemeMemberLimitRepository.save(limit);
        log.info("Saved new claim transaction with ID: {}", claimTransaction.getId());
        return claimTransaction;
    }

    private void creditClaimTransaction(double currentBalance, SchemeMemberLimit limit, double claimAmount) {
        log.info("Crediting amount to limit balance. Before: {}", currentBalance);
        limit.setLimitBalance(currentBalance + claimAmount);
        if (limit.getLimitOverUtilized() > 0) {
            double overUtilization = limit.getLimitOverUtilized();
            log.info("Handling overutilization refund. Previous overutilization: {}", overUtilization);
            if (overUtilization >= claimAmount) {
                limit.setLimitOverUtilized(overUtilization - claimAmount);
            } else {
                limit.setLimitOverUtilized(0.0);
                double diff = claimAmount - overUtilization;
                limit.setLimitUtilized(limit.getLimitUtilized() - diff);
            }
        } else {
            limit.setLimitUtilized(limit.getLimitUtilized() - claimAmount);
        }
        log.info("After credit, limit balance: {}, utilized: {}, overutilization: {}", limit.getLimitBalance(), limit.getLimitUtilized(), limit.getLimitOverUtilized());
    }

    private void debitClaimTransaction(double currentBalance, SchemeMemberLimit limit, double claimAmount) {
        log.info("Deducting amount from limit balance. Before: {}", currentBalance);

        limit.setLimitBalance(currentBalance - claimAmount);
        limit.setLimitUtilized(limit.getLimitUtilized() + claimAmount);
        if (currentBalance < claimAmount) {
            // If claim amount exceeds limit balance, throw exception
            log.warn("Claim amount exceeds limit balance. Current balance: {}, claim amount: {}", currentBalance, claimAmount);
            throw new ClaimTransactionException("Claim amount exceeds limit balance. Current balance: " + currentBalance + ", claim amount: " + claimAmount);
        }
        log.info("After deduction, limit balance: {}, utilized: {}", limit.getLimitBalance(), limit.getLimitUtilized());
    }

    private ClaimTransaction getClaimTransaction(Double amount, InsuranceClaim claim, Benefit benefit, ClaimStatus claimStatus, String operation, SchemeMemberLimit limit) {
        ClaimTransaction claimTransaction;
        claimTransaction = new ClaimTransaction();
        claimTransaction.setClaim(claim);
        claimTransaction.setBenefit(benefit);
        claimTransaction.setAmount(amount);
        claimTransaction.setClaimStatus(claimStatus);
        claimTransaction.setOperation(operation);
        claimTransaction.setLimitAmount(limit.getLimitAmount());
        claimTransaction.setLimitUtilization(limit.getLimitUtilized());
        claimTransaction.setLimitOverUtilization(limit.getLimitOverUtilized());
        claimTransactionRepository.save(claimTransaction);
        return claimTransaction;
    }

    private ClaimTransaction getClaimTransactionReversal(InsuranceClaim claim, Benefit benefit, double claimAmount, ClaimStatus claimStatus, String operation, SchemeMemberLimit limit) {
        ClaimTransaction reversalTransaction = new ClaimTransaction();
        reversalTransaction.setClaim(claim);
        reversalTransaction.setBenefit(benefit);
        reversalTransaction.setAmount(claimAmount);
        reversalTransaction.setClaimStatus(claimStatus);
        reversalTransaction.setOperation(operation.equals(ClaimTransactionOperation.DEBIT) ? ClaimTransactionOperation.CREDIT : ClaimTransactionOperation.DEBIT);
        reversalTransaction.setLimitAmount(limit.getLimitAmount());
        reversalTransaction.setLimitUtilization(limit.getLimitUtilized());
        reversalTransaction.setLimitOverUtilization(limit.getLimitOverUtilized());
        claimTransactionRepository.save(reversalTransaction);
        return reversalTransaction;
    }

    /**
     * Maps claim status string to the corresponding status code constant.
     * 
     * @param status The claim status as a string.
     * @return The corresponding status code.
     */
    private String getClaimStatusCode(String status) {
        String trimmedStatus = status == null ? "" : status.trim();
        if (ClaimStatuses.IN_REVIEW.equalsIgnoreCase(trimmedStatus)) {
            return BMSStatuses.IN_REVIEW;
        }
        if (ClaimStatuses.SENT_BACK.equalsIgnoreCase(trimmedStatus)) {
            return BMSStatuses.SENT_BACK;
        }
        if (ClaimStatuses.SENT_TO_SURVEILLANCE.equalsIgnoreCase(trimmedStatus)) {
            return BMSStatuses.SENT_TO_SURVEILLANCE;
        }
        if (ClaimStatuses.CLINICAL_REVIEW.equalsIgnoreCase(trimmedStatus)) {
            return BMSStatuses.CLINICAL_REVIEW;
        }
        if (ClaimStatuses.PAYMENT_COMPLETED.equalsIgnoreCase(trimmedStatus)) {
            return BMSStatuses.PAYMENT_COMPLETED;
        }
        if (ClaimStatuses.SENT_FOR_PAYMENT_PROCESSING.equalsIgnoreCase(trimmedStatus)) {
            return BMSStatuses.SENT_FOR_PAYMENT_PROCESSING;
        }
        if (ClaimStatuses.APPROVED.equalsIgnoreCase(trimmedStatus)) {
            return BMSStatuses.APPROVED;
        }
        if (ClaimStatuses.REJECTED.equalsIgnoreCase(trimmedStatus)) {
            return BMSStatuses.REJECTED;
        }
        if (ClaimStatuses.MEDICAL_REVIEW.equalsIgnoreCase(trimmedStatus)) {
            return BMSStatuses.MEDICAL_REVIEW;
        }
        if (ClaimStatuses.QUEUED.equalsIgnoreCase(trimmedStatus)) {
            return BMSStatuses.QUEUED;
        }
        if (ClaimStatuses.DECLINED.equalsIgnoreCase(trimmedStatus)) {
            return BMSStatuses.DECLINED;
        }
        throw new ClaimTransactionException("Invalid claim status: " + status);
    }

}
