package io.coredge.health.claims.db.repositories;

import camundajar.impl.scala.None;
import io.coredge.health.claims.db.entity.ErpClaims;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ErpClaimsRepository extends JpaRepository<ErpClaims, Long> {
    Optional<Boolean> existsByClaimNoAndHttpCallStatusIn(String claimNo, Collection<String> httpCallStatuses);

    Optional<Boolean> existsByClaimNoAndPaymentStatus(String claimNo, String paymentStatus);

    Optional<ErpClaims> findByClaimNo(String claimNo);

    Optional<Boolean> existsByClaimNo(String claimNo);

    List<ErpClaims> findAllByClaimNoIn(List<String> claimNos);

    Optional<List<ErpClaims>> findByParentClaimNo(String parentClaimNo);

    List<ErpClaims> findByClaimNoStartingWith(String claimNoPrefix);
}
