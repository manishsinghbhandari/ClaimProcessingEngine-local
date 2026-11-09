package io.coredge.health.claims.db.repositories.BMS;

import io.coredge.health.claims.db.entity.BMS.Benefit;
import io.coredge.health.claims.db.entity.BMS.SchemeDetail;
import io.coredge.health.claims.db.entity.BMS.SchemeMember;
import io.coredge.health.claims.db.entity.BMS.SchemeMemberLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchemeMemberLimitRepository extends JpaRepository<SchemeMemberLimit, Long> {

    List<SchemeMemberLimit> findBySchemeMemberId(Long schemeMemberId);

    Optional<SchemeMemberLimit> findBySchemeMemberAndBenefitAndSchemeDetail(
            SchemeMember schemeMember,
            Benefit benefit,
            SchemeDetail schemeDetail
    );

    List<SchemeMemberLimit> findBySchemeMemberAndBenefitInAndSchemeDetail(
            SchemeMember schemeMember,
            List<Benefit> benefits,
            SchemeDetail schemeDetail
    );

}
