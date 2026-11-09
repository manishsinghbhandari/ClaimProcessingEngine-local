package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.HieProvider;
import io.coredge.health.claims.db.entity.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HieProviderRepository extends JpaRepository<HieProvider, Long> {

  Optional<List<Provider>> findByMflCode(String mflCode);

  Optional<List<Provider>> findByRegNo(String regNo);

  Optional<Page<Provider>> findByLevelIgnoreCase(String level, Pageable pageable);

  Optional<Boolean> existsByMflCode(String mflCode);

  Optional<Boolean> existsByIdentifier(String identifier);

  Optional<List<Provider>> findByIdentifier(String identifier);

}
