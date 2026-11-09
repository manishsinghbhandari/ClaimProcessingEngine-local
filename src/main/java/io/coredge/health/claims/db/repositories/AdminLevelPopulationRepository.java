package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.AdminLevelOne;
import io.coredge.health.claims.db.entity.AdminLevelPopulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminLevelPopulationRepository extends JpaRepository<AdminLevelPopulation, Long> {
    List<AdminLevelPopulation> findAllByMonth(String month);
    
    Optional<AdminLevelPopulation> findByMonthAndAdminLevelEntity(String month, AdminLevelOne adminLevelEntity);
    
    int deleteByAdminLevelEntityAndMonth(AdminLevelOne adminLevelEntity, String month);
}

