package io.coredge.health.claims.utils;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.models.RuleInputDTO;
import io.coredge.health.claims.service.RuleProcessingService;
import io.coredge.health.claims.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.coredge.health.claims.constants.Constants.APPROVED_EXTENSION_STATES;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_LOS;

@Slf4j
@Service
public class BifurcationService {

    @Autowired
    private InsuranceClaimsRepository claimsRepository;

    @Autowired
    private RuleProcessingService ruleProcessingService;

    @Autowired
    private PreAuthLimits preAuthLimits;

    @Value("${SHA_06_ECCIF_Limit}")
    private int additionalEccifLimit;

    /**
     * Outcome of ECCIF‑SHA segregation for a single intervention.
     *
     * @param eccifApproved  true amount may be applied to ECCIF
     * @param eccifAmount    amount actually approved (zero if not applicable)
     * @param limitExceeded  true request would breach the ECCIF limit
     * @param exceedingAmount amount by which request exceeds the remaining limit (zero if not exceeded)
     */
    public record EccifSegregationResult(
            boolean eccifApproved,
            BigDecimal eccifAmount,
            boolean limitExceeded,
            BigDecimal exceedingAmount,
            boolean shifApproved,
            BigDecimal shifAmount
    ) {
        public static final EccifSegregationResult NO_ACTION =
                new EccifSegregationResult(false, BigDecimal.ZERO, false, BigDecimal.ZERO, false, BigDecimal.ZERO);
    }

    /**
     * Segregates the (PMF) and (SHA) amount based on intervention rules and decision inputs.
     * If the amount exceeds the SHA limit, we will pass on the remaining amount to Livia (PMF) for processing.
     *
     * @param interventionCode   The unique code representing the intervention to process.
     * @param adjudicationRecord The adjudication record containing relevant rule mappings and approval status.
     * @param decisionInputs     A map of decision inputs, including claim amounts and other variables.
     * @param pmfAmountMap       A map to store the calculated PMF amount against the intervention code.
     * @param isRenalRuleFailed  Indicates if the renal rule has failed, which is a special case.
     */
    public void segregatePMFSHAAmount(String interventionCode, int sequence, WorkflowService.AdjudicationRecord adjudicationRecord,
                                      Map<String, Object> decisionInputs, Map<String, BigDecimal> pmfAmountMap,
                                      EccifSegregationResult eccifSegregationResult, boolean isRenalRuleFailed,
                                      Optional<BigDecimal> thresholdAmount, BigDecimal finalApprovedPrice,
                                      Optional<BigDecimal> rehabLimit) {

        // 1. Handle a renal rule failure case (special case)
        if (isRenalRuleFailed) {
            BigDecimal claimedAmount = getClaimedAmount(decisionInputs);
            if (claimedAmount != null) {
                pmfAmountMap.put(interventionCode + "_" + sequence, claimedAmount);
                log.info("Renal care PMF amount: {} for Code: {}", claimedAmount, interventionCode);
            }
            return;
        }

        // 2. Rehab LOS-based limit override
        if (rehabLimit.isPresent()) {
            BigDecimal claimedAmount = getClaimedAmount(decisionInputs);
            if (claimedAmount != null) {
                BigDecimal limit = rehabLimit.get();
                if (claimedAmount.compareTo(limit) > 0) {
                    BigDecimal pmfDiff = claimedAmount.subtract(limit);
                    pmfAmountMap.put(interventionCode + "_" + sequence, pmfDiff);
                    log.info("Rehab PMF amount: {} (SHIF limit: {}) for Code: {}", pmfDiff, limit, interventionCode);
                } else {
                    log.info("Rehab claim {} within SHIF limit {} for Code: {}. No PMF.", claimedAmount, limit, interventionCode);
                }
            }
            return;
        }

        // 3. Skip if auto-approved or no rules to process
        if (adjudicationRecord.isAutoApproved() || adjudicationRecord.ruleMap().isEmpty()) {
            // Tariff threshold: when auto-approved, PMF = claimedAmount - thresholdAmount
            if (thresholdAmount.isPresent()) {
                BigDecimal claimedAmount = getClaimedAmount(decisionInputs);
                if (claimedAmount != null) {
                    BigDecimal limit = thresholdAmount.get().min(claimedAmount);
                    BigDecimal pmfDiff = claimedAmount.subtract(limit);
                    if (pmfDiff.compareTo(BigDecimal.ZERO) > 0) {
                        pmfAmountMap.put(interventionCode + "_" + sequence, pmfDiff);
                        log.info("Tariff threshold PMF amount: {} (ECCIF limit: {}) for Code: {}", pmfDiff, limit, interventionCode);
                    }
                }
            }
            return;
        }

        log.info("Processing PMF segregation for intervention code: {}", interventionCode);

        // 3. Process rules and check for failures
        Map<String, String> ruleMap = adjudicationRecord.ruleMap();
        Map<String, RuleInputDTO> processedRules = ruleProcessingService.processRules(ruleMap, decisionInputs);
        if (processedRules.isEmpty()) {
            return;
        }

        // 4. Check for non-allowed rule failures
        Set<String> allowedCountFailers = Set.of(VAR_INTERVENTION_USE_CNT, VAR_HOUSE_HOLD_USE_CNT);
        Set<String> allowedAmountFailers = Set.of(VAR_CLAIM_AMT, VAR_INDIVIDUAL_USAGE);

        boolean hasNonAllowedFailure = processedRules.entrySet().stream().anyMatch(e ->
                (!allowedCountFailers.contains(e.getKey()) && !allowedAmountFailers.contains(e.getKey()))
                        && "FAIL".equalsIgnoreCase(e.getValue().getResult()));

        if (hasNonAllowedFailure) {
            return;
        }

        // 5. Get claimed amount
        BigDecimal claimedAmount = getClaimedAmount(decisionInputs);

        if (claimedAmount == null) {
            return;
        }

        // 6. Determine PMF amount
        BigDecimal pmfAmount = null;

        boolean hasAllowedCountFailure = allowedCountFailers.stream().anyMatch(k -> "FAIL".equalsIgnoreCase(
                Optional.ofNullable(processedRules.get(k)).map(RuleInputDTO::getResult).orElse("PASS")));

        if (hasAllowedCountFailure) {
            pmfAmount = claimedAmount;
        }

        // If no count rules failed, check amount rules
        if(pmfAmount == null) {
            // Check allowed amount rules first
            String failingAmountRule = allowedAmountFailers.stream().filter(k -> "FAIL".equalsIgnoreCase(
                            Optional.ofNullable(processedRules.get(k)).map(RuleInputDTO::getResult).orElse("PASS")))
                    .findFirst().orElse(null);

            if (failingAmountRule != null) {
                String limitStr = ruleMap.get(failingAmountRule);
                if (limitStr != null && !limitStr.isBlank()) {
                    BigDecimal limit = extractLimitAmount(limitStr, decisionInputs);

                    if (eccifSegregationResult.limitExceeded) {
                        pmfAmount = eccifSegregationResult.exceedingAmount;
                    } else if (eccifSegregationResult.eccifApproved) {
                        pmfAmount = null; // no PMF if ECCIF is approved
                    } else if (claimedAmount.compareTo(limit) > 0) {
                        pmfAmount = claimedAmount.subtract(limit);
                    } else {
                        pmfAmount = claimedAmount;
                    }
                }
            }
        }

        // 7. Store the result if valid
        if (pmfAmount != null) {
            pmfAmountMap.put(interventionCode + "_" + sequence, pmfAmount);
            log.info("PMF amount: {} for Code: {}", pmfAmount, interventionCode);
        }
    }

    private BigDecimal getClaimedAmount(Map<String, Object> decisionInputs) {
        return Optional.ofNullable(decisionInputs.get(VAR_CLAIM_AMT))
                .map(v -> switch (v) {
                    case Integer i -> BigDecimal.valueOf(i);
                    case Double d -> BigDecimal.valueOf(d);
                    case String s -> new BigDecimal(s);
                    default -> null;
                })
                .orElse(null);
    }

    /**
     * Segregates the (ECCIF) and (SHA) amount based on intervention rules and decision inputs.
     * If the amount exceeds the SHA limit, we will pass on the remaining amount to ECCIF fund for processing.
     *
     * @param interventionCode    The unique code representing the intervention to process.
     * @param adjudicationRecord  The adjudication record containing relevant rule mappings and approval status.
     * @param decisionInputs      A map of decision inputs, including claim amounts and other variables.
     * @param claimId             The unique identifier of the claim being processed.
     * @param claimedAmount       The amount claimed for the intervention.
     * @param use                 The use of the claim, either preauthorization or claim.
     * @param packageCode         The package code associated with the intervention.
     * @param patientCrId         The CR ID of the patient associated with the claim.
     * @param isCivilServantClaim Indicates if the claim is for a civil servant, which may have different rules.
     */
    public EccifSegregationResult segregateECCIFSHAAmount(String interventionCode, WorkflowService.AdjudicationRecord adjudicationRecord,
                                                          Map<String, Object> decisionInputs, String claimId, double claimedAmount, String use,
                                                          String packageCode, String patientCrId, boolean isCivilServantClaim, LocalDate policyStartDate, LocalDate policyEndDate,
                                                          Optional<BigDecimal> thresholdAmount) {

        if (adjudicationRecord.isAutoApproved() || adjudicationRecord.ruleMap().isEmpty()) {
            // Tariff threshold: when auto-approved, ECCIF gets the threshold amount (not the full claim)
            if (thresholdAmount.isPresent()) {
                BigDecimal approvedPrice = thresholdAmount.get().min(BigDecimal.valueOf(claimedAmount));
                return new EccifSegregationResult(true, approvedPrice, false, BigDecimal.ZERO, false, BigDecimal.ZERO);
            }
            return EccifSegregationResult.NO_ACTION;
        }

        log.info("Processing ECCIF segregation for intervention code: {}", interventionCode);

        // ---- rule validation (only individualUsage may fail) -----------------
        Map<String, RuleInputDTO> processed = ruleProcessingService.processRules(adjudicationRecord.ruleMap(), decisionInputs);

        boolean otherRuleFailed = false;
        if (isCivilServantClaim) {
            Set<String> allowed = Set.of(VAR_CLAIM_AMT, VAR_INDIVIDUAL_USAGE);
            otherRuleFailed = processed.entrySet().stream().anyMatch(e -> !allowed.contains(e.getKey())
                    && "FAIL".equalsIgnoreCase(e.getValue().getResult()));
        } else {
            otherRuleFailed = processed.entrySet().stream().anyMatch(e -> !VAR_INDIVIDUAL_USAGE.equalsIgnoreCase(e.getKey())
                    && "FAIL".equalsIgnoreCase(e.getValue().getResult()));
        }


        if (processed.isEmpty() || otherRuleFailed) {
            return EccifSegregationResult.NO_ACTION;
        }

        // ---- totals used earlier --------------------------------------------
        BigDecimal eccifUsedEarlier = use.equals(Constants.Use.PREAUTHORIZATION)
                ? preAuthLimits.getTotalApprovedEccifAmountWithExpiryAndConsumptionCheck(patientCrId, APPROVED_EXTENSION_STATES, packageCode, use, policyStartDate, policyEndDate, null)
                : claimsRepository.getTotalEccifAmtOfClaimsByMemberCrIdAndCode(patientCrId, APPROVED_EXTENSION_STATES, packageCode, use, policyStartDate, policyEndDate);

        BigDecimal shifUsedEarlier = use.equals(Constants.Use.PREAUTHORIZATION)
                ? preAuthLimits.getTotalApprovedShifAmountWithConsumptionCheck(patientCrId, APPROVED_EXTENSION_STATES, packageCode, use, policyStartDate, policyEndDate, null)
                : claimsRepository.getTotalShifAmtOfClaimsByMemberCrIdAndCode(patientCrId, APPROVED_EXTENSION_STATES, packageCode, use, policyStartDate, policyEndDate);

        // ---- inputs & limits -------------------------------------------------
        var ruleMap = adjudicationRecord.ruleMap();
        var limitStrOpt = Optional.ofNullable(ruleMap.get(VAR_INDIVIDUAL_USAGE));
        var indivUsageOpt = Optional.ofNullable(decisionInputs.get(VAR_INDIVIDUAL_USAGE)).map(this::convertToBigDecimal);

        if (limitStrOpt.isEmpty() || indivUsageOpt.isEmpty()) {
            log.info("Exiting Either limitStrOpt :{} isEmpty or indivUsageOpt :{} isEmpty / claim {}", limitStrOpt, indivUsageOpt, claimId);
            return EccifSegregationResult.NO_ACTION;
        }

        BigDecimal shifLimitAmount = extractLimitAmount(limitStrOpt.get(), decisionInputs);

        // Current claim amount
        BigDecimal currentClaimAmount = BigDecimal.valueOf(claimedAmount);

        // Calculate accurate usage from database
        BigDecimal previousTotalUsage = shifUsedEarlier.add(eccifUsedEarlier);
        BigDecimal accurateTotalUsage = previousTotalUsage.add(currentClaimAmount);

        // Calculate ECCIF amount for this claim using ACCURATE database values
        BigDecimal eccifAmountForThisClaim = calculateEccifAmountForThisClaim(
                shifUsedEarlier,  // Use accurate SHIF usage from database
                eccifUsedEarlier,  // Use accurate ECCIF usage from database
                shifLimitAmount,
                currentClaimAmount,
                isCivilServantClaim
        );

        // Calculate SHIF amount for this claim (what's left after ECCIF)
        BigDecimal shifAmountForThisClaim = currentClaimAmount.subtract(eccifAmountForThisClaim);
        boolean shifApplicable = shifAmountForThisClaim.compareTo(BigDecimal.ZERO) > 0;

        // Calculate civil servant excess if any
        BigDecimal civilServantExcess = BigDecimal.ZERO;
        if (isCivilServantClaim) {
            civilServantExcess = calculateCivilServantExcess(
                    shifUsedEarlier,
                    eccifUsedEarlier,
                    shifLimitAmount,
                    currentClaimAmount,
                    eccifAmountForThisClaim
            );
        }

        // Calculate total ECCIF usage (including previous)
        BigDecimal totalEccifUsage = eccifUsedEarlier.add(eccifAmountForThisClaim);
        BigDecimal totalPackageLimit = shifLimitAmount.add(BigDecimal.valueOf(additionalEccifLimit));

        log.info("ClaimId  : {} calculated values for ECCIF segregation , totalEccifUsage :{}, eccifAmountForThisClaim :{}, shifAmountForThisClaim :{} " +
                        "shifLimitAmount :{}, accurateTotalUsage :{}", claimId, totalEccifUsage, eccifAmountForThisClaim, shifAmountForThisClaim,
                shifLimitAmount, accurateTotalUsage);


        // For non-civil servants: check limits
        if (!isCivilServantClaim) {
            // CHECK 1: Total package limit (550,000) - HARD STOP
            if (accurateTotalUsage.compareTo(totalPackageLimit) > 0) {
                log.info("Total package limit exceeded for {} / claim {}", interventionCode, claimId);
                return new EccifSegregationResult(false, BigDecimal.ZERO, true, BigDecimal.ZERO, false, BigDecimal.ZERO);
            }

            // CHECK 2: ECCIF limit - only if claim uses ECCIF
            if (eccifAmountForThisClaim.compareTo(BigDecimal.ZERO) > 0) {
                // Don't allow ANY new ECCIF if already exceeded
                if (eccifUsedEarlier.compareTo(BigDecimal.valueOf(additionalEccifLimit)) >= 0) {
                    log.info("ECCIF limit already exceeded, rejecting additional ECCIF for {} / claim {}", interventionCode, claimId);
                    return new EccifSegregationResult(false, BigDecimal.ZERO, true, BigDecimal.ZERO, false, BigDecimal.ZERO);
                }

                // Don't allow first-time exceedance
                if (totalEccifUsage.compareTo(BigDecimal.valueOf(additionalEccifLimit)) > 0) {
                    log.info("This claim would exceed ECCIF limit for {} / claim {}", interventionCode, claimId);
                    BigDecimal exceedingAmount = previousTotalUsage.subtract(BigDecimal.valueOf(additionalEccifLimit));
                    return new EccifSegregationResult(false, BigDecimal.ZERO, true, exceedingAmount, false, BigDecimal.ZERO);
                }
            }

            boolean eccifApproved = eccifAmountForThisClaim.compareTo(BigDecimal.ZERO) > 0;
            return new EccifSegregationResult(eccifApproved, eccifAmountForThisClaim, false, BigDecimal.ZERO,
                    shifApplicable, shifAmountForThisClaim);
        }
        // For civil servants
        else {
            boolean hasEccif = eccifAmountForThisClaim.compareTo(BigDecimal.ZERO) > 0;
            boolean hasCivilExcess = civilServantExcess.compareTo(BigDecimal.ZERO) > 0;
            log.info("Civil Servant handling of eccif for {} / claim {} eccifAmount :{} , civilServantAmount : {}, shifAmount : {}",
                    interventionCode, claimId, eccifAmountForThisClaim, civilServantExcess, shifAmountForThisClaim);
            return new EccifSegregationResult(hasEccif, eccifAmountForThisClaim, hasCivilExcess, civilServantExcess,
                    shifApplicable, shifAmountForThisClaim);
        }
    }

    private BigDecimal calculateEccifAmountForThisClaim(BigDecimal shifUsedEarlier, BigDecimal eccifUsedEarlier,
                                                        BigDecimal shifLimitAmount, BigDecimal currentClaimAmount,
                                                        boolean isCivilServantClaim) {
        BigDecimal totalPackageLimit = shifLimitAmount.add(BigDecimal.valueOf(additionalEccifLimit));
        BigDecimal previousTotalUsage = shifUsedEarlier.add(eccifUsedEarlier);

        // If SHIF already exhausted
        if (shifUsedEarlier.compareTo(shifLimitAmount) >= 0) {
            if (!isCivilServantClaim) {
                // Non-civil servant: entire current claim is ECCIF
                return currentClaimAmount;
            } else {
                // Civil servant: check if within total package limit
                if (previousTotalUsage.compareTo(totalPackageLimit) >= 0) {
                    // Already exceeded total package limit - no ECCIF
                    return BigDecimal.ZERO;
                } else {
                    // Part might be within ECCIF range
                    BigDecimal remainingEccif = totalPackageLimit.subtract(previousTotalUsage);
                    return remainingEccif.min(currentClaimAmount);
                }
            }
        }
        // SHIF not exhausted yet
        else {
            BigDecimal remainingShif = shifLimitAmount.subtract(shifUsedEarlier);

            if (currentClaimAmount.compareTo(remainingShif) > 0) {
                // Claim exceeds remaining SHIF
                BigDecimal shifExcess = currentClaimAmount.subtract(remainingShif);

                if (!isCivilServantClaim) {
                    // Non-civil servant: excess is ECCIF
                    return shifExcess;
                } else {
                    // Civil servant: check if within total package limit
                    BigDecimal totalAfterClaim = previousTotalUsage.add(currentClaimAmount);

                    if (totalAfterClaim.compareTo(totalPackageLimit) <= 0) {
                        // Within total package limit - all excess is ECCIF
                        return shifExcess;
                    } else {
                        // Exceeds total package limit - only part is ECCIF
                        BigDecimal availableInPackage = totalPackageLimit.subtract(previousTotalUsage);
                        BigDecimal remainingAfterShif = availableInPackage.subtract(remainingShif);
                        return remainingAfterShif.max(BigDecimal.ZERO);
                    }
                }
            } else {
                // Entire claim fits in remaining SHIF - no ECCIF
                return BigDecimal.ZERO;
            }
        }
    }

    private BigDecimal calculateCivilServantExcess(BigDecimal shifUsedEarlier, BigDecimal eccifUsedEarlier,
                                                   BigDecimal shifLimitAmount, BigDecimal currentClaimAmount,
                                                   BigDecimal eccifAmountForThisClaim) {
        BigDecimal totalPackageLimit = shifLimitAmount.add(BigDecimal.valueOf(additionalEccifLimit));
        BigDecimal previousTotalUsage = shifUsedEarlier.add(eccifUsedEarlier);
        BigDecimal totalAfterClaim = previousTotalUsage.add(currentClaimAmount);

        // If total after claim exceeds total package limit
        if (totalAfterClaim.compareTo(totalPackageLimit) > 0) {
            // Civil servant excess = current claim amount - ECCIF amount
            return currentClaimAmount.subtract(eccifAmountForThisClaim);
        }

        return BigDecimal.ZERO;
    }

    /**
     * Extracts and calculates a limit amount from the provided string input.
     * If the input string contains "VAR_LOS" and the decisionInputs map contains a corresponding
     * integer value for the key "VAR_LOS", the returned amount is multiplied by the integer value.
     * Otherwise, extracts the numeric value from the string and returns it as a BigDecimal.
     *
     * @param amountStr the string that represents the amount, possibly containing non-numeric characters
     * @param decisionInputs a map containing input key-value pairs used in decision calculations
     * @return the calculated limit amount as a BigDecimal
     */
    private BigDecimal extractLimitAmount(String amountStr, Map<String, Object> decisionInputs) {
        BigDecimal amount = new BigDecimal(amountStr.replaceAll("[^\\d]", ""));

        if (amountStr.contains(VAR_LOS)) {
            Object losValue = decisionInputs.get(VAR_LOS);

            if (losValue == null) {
                throw new IllegalArgumentException("VAR_LOS is required but was not provided.");
            }

            BigDecimal los = switch (losValue) {
                case Integer intValue -> BigDecimal.valueOf(intValue);
                case Double doubleValue -> BigDecimal.valueOf(doubleValue);
                case String strValue -> new BigDecimal(strValue);
                default ->
                        throw new IllegalArgumentException("Unsupported type for VAR_LOS: " + losValue.getClass().getSimpleName());
            };

            return amount.multiply(los);
        }
        return amount;
    }

    private BigDecimal convertToBigDecimal(Object value) {
        return switch (value) {
            case Integer intValue -> BigDecimal.valueOf(intValue);
            case Double doubleValue -> BigDecimal.valueOf(doubleValue);
            case String strValue -> new BigDecimal(strValue);
            default -> null;
        };
    }


}
