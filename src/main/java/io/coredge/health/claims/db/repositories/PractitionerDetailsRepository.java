package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.PractitionerDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PractitionerDetailsRepository extends JpaRepository<PractitionerDetails, Long> {
  Optional<List<PractitionerDetails>> findByRegNo(String regNo);

  @Query("SELECT pd.regNo FROM PractitionerDetails pd")
  List<String> findAllPractitionerRegNo();

  Optional<Boolean> existsByRegNo(String regNo);
}
