package io.coredge.health.claims.db.repositories.BMS;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.coredge.health.claims.db.entity.BMS.SchemeJobGroup;
import io.coredge.health.claims.db.entity.BMS.SchemeDetail;

import java.util.List;

@Repository
public interface SchemeJobGroupRepository extends JpaRepository<SchemeJobGroup, String> {
    void deleteBySchemeDetail(SchemeDetail schemeDetail);

    List<SchemeJobGroup> findBySchemeDetailId(Long schemeDetailId);
}