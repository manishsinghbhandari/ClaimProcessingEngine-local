package io.coredge.health.claims.db.repositories.BMS;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.coredge.health.claims.db.entity.BMS.Benefit;
import io.coredge.health.claims.db.entity.BMS.JobGroup;
import io.coredge.health.claims.db.entity.BMS.SchemeBenefitLimit;
import io.coredge.health.claims.db.entity.BMS.SchemeDetail;

@Repository
public interface SchemeBenefitLimitRepository extends JpaRepository<SchemeBenefitLimit, Long> {
    Optional<SchemeBenefitLimit> findBySchemeDetailAndBenefitAndJobGroup(SchemeDetail schemeDetail, Benefit benefit, JobGroup jobGroup);
    List<SchemeBenefitLimit> findBySchemeDetail(SchemeDetail schemeDetail);
    List<SchemeBenefitLimit> findBySchemeDetailAndBenefitInAndJobGroup(
            SchemeDetail schemeDetail,
            List<Benefit> benefits,
            JobGroup jobGroup
    );
}