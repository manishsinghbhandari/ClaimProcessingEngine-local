package io.coredge.health.claims.service.validation;

import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * SHA-16 package validation strategy.
 * Rules:
 * 1. High-cost: approved_price > 10,650 KES
 * 2. Same-day duplicates: > 1 session per day
 * 3. Weekly session limit (dialysis): Civil servants 3/week, others 2/week
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Sha16ValidationStrategy implements PackageValidationStrategy {

    private final LineItemRepository lineItemRepository;

    private static final List<String> SURVEILLANCE_CODES = List.of("SHA-16-001", "SHA-16-002", "SHA-16-007");
    private static final List<String> DIALYSIS_CODES = List.of("SHA-16-001");
    private static final BigDecimal COST_THRESHOLD = new BigDecimal("10650");
    private static final int DIALYSIS_WEEKLY_LIMIT_CIVIL_SERVANT = 3;
    private static final int DIALYSIS_WEEKLY_LIMIT_OTHERS = 2;

    @Override
    public boolean supports(String interventionCode) {
        if (interventionCode == null) return false;
        return SURVEILLANCE_CODES.stream().anyMatch(interventionCode::startsWith);
    }

    private boolean isDialysisCode(String code) {
        if (code == null) return false;
        return DIALYSIS_CODES.stream().anyMatch(code::startsWith);
    }

    @Override
    public Optional<String> validate(InsuranceClaim claim) {
        // Fetch claim items from repository
        List<ClaimLineItem> claimItems = lineItemRepository.findByClaimId(claim.getClaimId())
                .orElse(List.of());
        if (claimItems.isEmpty()) {
            log.warn("No claim items found for claim {}", claim.getClaimId());
            return Optional.empty();
        }

        ClaimLineItem item = claimItems.get(0);
        boolean isCivilServant = claim.getCivilServantInvId() != null;
        String crId = item.getClaim().getPatient().getCrId();
        String code = item.getCode();
        LocalDate serviceDate = item.getServicedDate() != null 
                ? item.getServicedDate() 
                : item.getServicedPeriodStart();

        log.debug("Validating SHA-16 rules for crId={}, code={}, serviceDate={}, isCivilServant={}",
                crId, code, serviceDate, isCivilServant);

        // Rule 1: High-cost check
        if (item.getApprovedPrice() != null 
                && item.getApprovedPrice().compareTo(COST_THRESHOLD) > 0) {
            log.info("High-cost rule triggered: crId={}, code={}, approvedPrice={}", 
                    crId, code, item.getApprovedPrice());
            return Optional.of("approved_price > 10650");
        }

        if (serviceDate == null) {
            log.warn("No service date found for claim item, skipping date-based checks");
            return Optional.empty();
        }

        // Rule 2: Same-day duplicate check
        int sameDayCount = lineItemRepository.countSameDaySessions(crId, SURVEILLANCE_CODES, serviceDate);
        if (sameDayCount > 1) {
            log.info("Same-day duplicate rule triggered: crId={}, code={}, serviceDate={}, count={}",
                    crId, code, serviceDate, sameDayCount);
            return Optional.of(sameDayCount + " sessions same day");
        }

        // Rule 3: Weekly session limit (dialysis codes only)
        if (isDialysisCode(code)) {
            LocalDate weekStart = serviceDate.with(DayOfWeek.MONDAY);
            LocalDate weekEnd = serviceDate.with(DayOfWeek.SUNDAY);
            int weeklyCount = lineItemRepository.countWeeklySessions(crId, DIALYSIS_CODES, weekStart, weekEnd);
            int limit = isCivilServant ? DIALYSIS_WEEKLY_LIMIT_CIVIL_SERVANT : DIALYSIS_WEEKLY_LIMIT_OTHERS;
            
            if (weeklyCount > limit) {
                log.info("Weekly session limit rule triggered: crId={}, code={}, weeklyCount={}, limit={}, isCivilServant={}",
                        crId, code, weeklyCount, limit, isCivilServant);
                return Optional.of(weeklyCount + " sessions/week (limit: " + limit + ")");
            }
        }

        log.debug("All SHA-16 rules passed for crId={}, code={}", crId, code);
        return Optional.empty();
    }
}
