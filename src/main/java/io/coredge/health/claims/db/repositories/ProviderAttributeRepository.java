package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.entity.ProviderAttributes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderAttributeRepository extends JpaRepository<ProviderAttributes , Long> {

    ProviderAttributes findByProvider(Provider provider);
}
