package io.coredge.health.claims.db.repositories.BMS;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.coredge.health.claims.db.entity.BMS.SchemeBenefit;
import io.coredge.health.claims.db.entity.BMS.SchemeDetail;

import java.util.List;

@Repository
public interface SchemeBenefitRepository extends JpaRepository<SchemeBenefit, Long> {
    void deleteBySchemeDetail(SchemeDetail schemeDetail);

    List<SchemeBenefit> findBySchemeDetailId(Long schemeDetailId);
}