package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.ClaimItemDetail;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimItemDetailRepository extends JpaRepository<ClaimItemDetail, Long> {

    List<ClaimItemDetail> findByClaimLineItem(ClaimLineItem claimLineItem);

    List<ClaimItemDetail> findByClaimLineItemId(Long itemId);
}
