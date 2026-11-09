package io.coredge.health.claims.db.repositories.BMS;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.coredge.health.claims.db.entity.BMS.Benefit;
import java.util.List;
import java.util.Optional;

@Repository
public interface BenefitRepository extends JpaRepository<Benefit, Long> {

    // Find by code
    Optional<Benefit> findByCode(String code);

    // Find by name
    List<Benefit> findByName(String name);

    // Delete by code
    void deleteByCode(String code);

    List<Benefit> findByCodeIn(List<String> codes);

}
