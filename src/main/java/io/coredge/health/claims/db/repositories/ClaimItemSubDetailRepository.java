package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.ClaimItemDetail;
import io.coredge.health.claims.db.entity.ClaimItemSubDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimItemSubDetailRepository extends JpaRepository<ClaimItemSubDetail, Long> {

    List<ClaimItemSubDetail> findByClaimItemDetail(ClaimItemDetail claimItemDetail);

    List<ClaimItemSubDetail> findByClaimItemDetailId(Long detailId);
}
