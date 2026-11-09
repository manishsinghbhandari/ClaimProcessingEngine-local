package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.SystemUtilizationCharge;
import io.coredge.health.claims.db.entity.SystemUtilizationChargeView;
import io.coredge.health.claims.enums.Status;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemUtilizationChargeRepository extends JpaRepository<SystemUtilizationCharge, Long>{

    List<SystemUtilizationChargeView> findByStatusOrderByEffectiveDate(Status status);
}
