package io.coredge.health.claims.service.validation;

import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Sha16ValidationStrategyTest {

    @Mock
    private LineItemRepository lineItemRepository;

    @InjectMocks
    private Sha16ValidationStrategy sha16ValidationStrategy;

    private ClaimLineItem claimItem;
    private InsuranceClaim claim;
    private Member patient;

    @BeforeEach
    void setUp() {
        patient = new Member();
        patient.setCrId("CR-12345");

        claim = new InsuranceClaim();
        claim.setClaimId(UUID.randomUUID().toString());
        claim.setPatient(patient);

        claimItem = new ClaimLineItem();
        claimItem.setClaim(claim);
        claimItem.setServicedDate(LocalDate.of(2026, 1, 15));
    }

    /**
     * Helper: set up the claim for validation with the given code, price, and civil servant status.
     * Mocks lineItemRepository.findByClaimId() to return the configured claimItem.
     */
    private void setupClaim(String code, BigDecimal approvedPrice, boolean isCivilServant) {
        claimItem.setCode(code);
        claimItem.setApprovedPrice(approvedPrice);
        if (isCivilServant) {
            claim.setCivilServantInvId("CS-INV-001");
        } else {
            claim.setCivilServantInvId(null);
        }
        when(lineItemRepository.findByClaimId(any(UUID.class))).thenReturn(Optional.of(List.of(claimItem)));
    }

    @Nested
    @DisplayName("supports()")
    class SupportsTests {

        @Test
        @DisplayName("should return true for SHA-16-001 codes")
        void shouldReturnTrueForSha16001() {
            assertThat(sha16ValidationStrategy.supports("SHA-16-001")).isTrue();
            assertThat(sha16ValidationStrategy.supports("SHA-16-001-001")).isTrue();
        }

        @Test
        @DisplayName("should return true for SHA-16-002 codes")
        void shouldReturnTrueForSha16002() {
            assertThat(sha16ValidationStrategy.supports("SHA-16-002")).isTrue();
        }

        @Test
        @DisplayName("should return true for SHA-16-007 codes")
        void shouldReturnTrueForSha16007() {
            assertThat(sha16ValidationStrategy.supports("SHA-16-007")).isTrue();
        }

        @Test
        @DisplayName("should return false for non-surveillance codes")
        void shouldReturnFalseForNonSurveillanceCodes() {
            assertThat(sha16ValidationStrategy.supports("SHA-01-001")).isFalse();
            assertThat(sha16ValidationStrategy.supports("SHA-16-003")).isFalse();
            assertThat(sha16ValidationStrategy.supports(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("validate() - High-Cost Rule")
    class HighCostRuleTests {

        @Test
        @DisplayName("should reject when approved_price > 10650")
        void shouldRejectWhenPriceExceedsThreshold() {
            setupClaim("SHA-16-001", new BigDecimal("10651"), false);

            Optional<String> result = sha16ValidationStrategy.validate(claim);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("approved_price > 10650");
        }

        @Test
        @DisplayName("should pass when approved_price = 10650")
        void shouldPassWhenPriceEqualsThreshold() {
            setupClaim("SHA-16-001", new BigDecimal("10650"), false);

            when(lineItemRepository.countSameDaySessions(any(), any(), any())).thenReturn(1);
            when(lineItemRepository.countWeeklySessions(any(), any(), any(), any())).thenReturn(1);

            Optional<String> result = sha16ValidationStrategy.validate(claim);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should pass when approved_price < 10650")
        void shouldPassWhenPriceBelowThreshold() {
            setupClaim("SHA-16-001", new BigDecimal("5000"), false);

            when(lineItemRepository.countSameDaySessions(any(), any(), any())).thenReturn(1);
            when(lineItemRepository.countWeeklySessions(any(), any(), any(), any())).thenReturn(1);

            Optional<String> result = sha16ValidationStrategy.validate(claim);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("validate() - Same-Day Duplicate Rule")
    class SameDayDuplicateRuleTests {

        @Test
        @DisplayName("should reject when more than 1 session on same day")
        void shouldRejectWhenMultipleSameDaySessions() {
            setupClaim("SHA-16-002", new BigDecimal("5000"), false);

            when(lineItemRepository.countSameDaySessions(
                    eq("CR-12345"),
                    any(),
                    eq(LocalDate.of(2026, 1, 15))
            )).thenReturn(2);

            Optional<String> result = sha16ValidationStrategy.validate(claim);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("2 sessions same day");
        }

        @Test
        @DisplayName("should pass when only 1 session on same day")
        void shouldPassWhenSingleSession() {
            setupClaim("SHA-16-002", new BigDecimal("5000"), false);

            when(lineItemRepository.countSameDaySessions(any(), any(), any())).thenReturn(1);

            Optional<String> result = sha16ValidationStrategy.validate(claim);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("validate() - Weekly Session Limit (Dialysis)")
    class WeeklySessionLimitTests {

        @Test
        @DisplayName("should reject non-civil-servant with >2 sessions/week for SHA-16-001")
        void shouldRejectNonCivilServantExceedingLimit() {
            setupClaim("SHA-16-001", new BigDecimal("5000"), false);

            when(lineItemRepository.countSameDaySessions(any(), any(), any())).thenReturn(1);
            when(lineItemRepository.countWeeklySessions(any(), any(), any(), any())).thenReturn(3);

            Optional<String> result = sha16ValidationStrategy.validate(claim);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("3 sessions/week");
            assertThat(result.get()).contains("limit: 2");
        }

        @Test
        @DisplayName("should pass civil-servant with 3 sessions/week for SHA-16-001")
        void shouldPassCivilServantWithinLimit() {
            setupClaim("SHA-16-001", new BigDecimal("5000"), true);

            when(lineItemRepository.countSameDaySessions(any(), any(), any())).thenReturn(1);
            when(lineItemRepository.countWeeklySessions(any(), any(), any(), any())).thenReturn(3);

            Optional<String> result = sha16ValidationStrategy.validate(claim);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should reject civil-servant with >3 sessions/week for SHA-16-001")
        void shouldRejectCivilServantExceedingLimit() {
            claimItem.setCode("SHA-16-001");
            claimItem.setApprovedPrice(new BigDecimal("5000"));
            claim.setCivilServantInvId("CS-INV-001");
            
            when(lineItemRepository.findByClaimId(any(UUID.class))).thenReturn(Optional.of(List.of(claimItem)));
            when(lineItemRepository.countSameDaySessions(any(), any(), any())).thenReturn(1);
            when(lineItemRepository.countWeeklySessions(any(), any(), any(), any())).thenReturn(4);

            Optional<String> result = sha16ValidationStrategy.validate(claim);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("4 sessions/week");
            assertThat(result.get()).contains("limit: 3");
        }

        @Test
        @DisplayName("should not apply weekly limit to SHA-16-002")
        void shouldNotApplyWeeklyLimitToNonDialysisCode() {
            setupClaim("SHA-16-002", new BigDecimal("5000"), false);

            when(lineItemRepository.countSameDaySessions(any(), any(), any())).thenReturn(1);

            Optional<String> result = sha16ValidationStrategy.validate(claim);

            assertThat(result).isEmpty();
        }
    }
}
