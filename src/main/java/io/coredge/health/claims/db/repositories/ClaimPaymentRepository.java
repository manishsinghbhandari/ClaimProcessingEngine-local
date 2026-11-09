package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.ClaimPaymentProjection;
import io.coredge.health.claims.db.entity.ClaimPayment;
import io.coredge.health.claims.enums.FundType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClaimPaymentRepository extends JpaRepository<ClaimPayment, Long> {

    @Query("""
                SELECT cp.claim.claimedTotal as claimedTotal,
                       cp.claim.approvedTotal as approvedTotal,
                       cp.claim.claimPayableAmount as totalPayableAmount,
                       cp.claim.systemUtilizationAmount as totalUtilizationAmount,
                       cp.fundType as fundType,
                       cp.claimedAmount as claimedAmount,
                       cp.facilityPayableAmount as facilityPayableAmount,
                       cp.systemUtilizationAmount as utilizationAmount
                FROM ClaimPayment cp
                WHERE cp.claim.claimId = :claimId
            """)
    List<ClaimPaymentProjection> findByClaimId(@Param("claimId") UUID claimId);

    Optional<ClaimPayment> findByClaim_ClaimIdAndFundType(UUID claimId, FundType fundType);

    @Transactional
    default ClaimPayment saveOrUpdate(ClaimPayment newPayment) {

        return findByClaim_ClaimIdAndFundType(newPayment.getClaim().getClaimId(), newPayment.getFundType())
                .map(existing -> {
                    // Update fields
                    existing.setClaimedAmount(newPayment.getClaimedAmount());
                    existing.setFacilityPayableAmount(newPayment.getFacilityPayableAmount());
                    existing.setSystemUtilizationAmount(newPayment.getSystemUtilizationAmount());
                    return save(existing);
                })
                .orElseGet(() -> save(newPayment)
                );
    }
}
