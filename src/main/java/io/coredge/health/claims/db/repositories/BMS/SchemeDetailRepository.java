package io.coredge.health.claims.db.repositories.BMS;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.coredge.health.claims.db.entity.BMS.SchemeDetail;

public interface SchemeDetailRepository extends JpaRepository<SchemeDetail, Long> {

    @Query("SELECT COALESCE(MAX(s.autoNumber), 0) FROM SchemeDetail s")
    Integer findMaxAutoNumber();

    Optional<SchemeDetail> findByAutoCode(String autoCode);

    Optional<SchemeDetail> findBySystemCode(String systemCode);
}
