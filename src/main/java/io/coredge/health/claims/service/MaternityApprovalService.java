package io.coredge.health.claims.service;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;

import static io.coredge.health.claims.constants.Constants.APPROVED_EXTENSION_STATES;

@Slf4j
@Service
public class MaternityApprovalService {

    private static final String MATERNITY_CODE_PREFIX = "SHA-08";
    private static final int AGE_REQ_DOC_AUTH = 55;
    private static final long MATERNITY_MONTH_THRESHOLD = 10;

    private static final Set<String> MATERNITY_MUST_DOC_REVIEW = Set.of("SHA-08-004", "SHA-08-006");
    private static final Set<String> MATERNITY_MUST_DOC_REVIEW_COND = Set.of("SHA-08-005");
    private static final Set<String> MATERNITY_CHECK_LAST_10_MONTHS = Set.of("SHA-08-005", "SHA-08-006");

    private final MemberService memberService;

    public MaternityApprovalService(MemberService memberService) {
        this.memberService = memberService;
    }

    public boolean isDocApprovalMaternityNewBornHealth(String interventionCode, Member patient) {
        validateInput(interventionCode, patient);

        if (!isMaternityIntervention(interventionCode)) {
            return false;
        }

        int age = FhirHelper.calculateAge(patient.getBirthDate());

        // Check if unconditional doctor review is required
        if (isReviewAlwaysRequired(interventionCode)) {
            return true;
        }
        // Check if conditional review is needed based on age
        if (isReviewConditionallyRequired(interventionCode, age)) {
            return true;
        }
        return false;
    }

    private boolean isMaternityIntervention(String code) {
        return code != null && code.startsWith(MATERNITY_CODE_PREFIX);
    }

    private boolean isReviewAlwaysRequired(String code) {
         // All claims must undergo clinical review
        return MATERNITY_MUST_DOC_REVIEW.contains(code);
    }

    private boolean isReviewConditionallyRequired(String code, int age) {
        // >55 years shall be manually approved
        return MATERNITY_MUST_DOC_REVIEW_COND.contains(code) && age > AGE_REQ_DOC_AUTH;
    }

    public boolean isLookBackRequired(String code) {
        // *Must not have delivered in 10 months
        return MATERNITY_CHECK_LAST_10_MONTHS.contains(code);
    }

    private void validateInput(String code, Member patient) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Intervention code must not be null or blank.");
        }
        if (patient == null || patient.getBirthDate() == null || patient.getCrId() == null) {
            throw new IllegalArgumentException("Patient data is incomplete.");
        }
    }

    public boolean isLastServiceOlderThanTenMonths(String patientCrId, String interventionCode) {
        log.info("Checking last maternity interventions for patientCrId: {}, interventionCode: {}", patientCrId, interventionCode);

        Optional<List<ClaimLineItem>> optionalLineItems =
                memberService.findByClaim_Patient_CrId(Constants.Use.CLAIM, patientCrId, APPROVED_EXTENSION_STATES);

        return optionalLineItems
                .filter(list -> !list.isEmpty())
                .map(list -> {
                    Optional<LocalDate> latestServiceDateOpt = list.stream()
                            .filter(item -> MATERNITY_CHECK_LAST_10_MONTHS.contains(item.getCode()))
                            .filter(item -> item.getStatus().equals(Constants.ClaimLineItemStatus.PASSED))
                            .map(item -> {
                                if (item.getServicedPeriodEnd() != null) {
                                    return item.getServicedPeriodEnd();
                                } else if (item.getServicedDate() != null) {
                                    return item.getServicedDate();
                                } else {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .max(LocalDate::compareTo);

                    LocalDate latestServiceDate = latestServiceDateOpt.orElse(null);
                    if (latestServiceDate == null) return true;

                    LocalDate currentDate = LocalDate.now();
                    long months = Period.between(latestServiceDate, currentDate).toTotalMonths();
                    return months > MATERNITY_MONTH_THRESHOLD;
                })
                .orElse(true); // No records = older than a threshold
    }

}
