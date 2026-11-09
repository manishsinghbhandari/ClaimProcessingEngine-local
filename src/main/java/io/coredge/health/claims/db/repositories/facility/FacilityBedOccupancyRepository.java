package io.coredge.health.claims.db.repositories.facility;


import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.entity.facility.FacilityBedOccupancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FacilityBedOccupancyRepository extends JpaRepository<FacilityBedOccupancy, Long> {

    List<FacilityBedOccupancy> findByProviderAndBedTypeInAndOccupancyDateIn(Provider provider, List<String> bedTypes, List<LocalDate> dates);

}
