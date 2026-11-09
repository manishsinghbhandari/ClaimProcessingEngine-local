package io.coredge.health.claims.db.repositories.facility;

import io.coredge.health.claims.db.entity.facility.FacilityBedCapacity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FacilityBedCapacityRepository extends JpaRepository<FacilityBedCapacity, Long> {

    @Query("""
    SELECT f FROM FacilityBedCapacity f
    WHERE f.id IN (
        SELECT MAX(f2.id) FROM FacilityBedCapacity f2
        WHERE f2.providerId IN :providerIds
        GROUP BY f2.providerId)
""")
    List<FacilityBedCapacity> findLatestByProviderIds(@Param("providerIds") List<Long> providerIds);

    FacilityBedCapacity findByProviderId(Long providerId);
}
