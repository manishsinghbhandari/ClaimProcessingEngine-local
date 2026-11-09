package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.SystemVendor;
import io.coredge.health.claims.db.entity.SystemVendorView;
import io.coredge.health.claims.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemVendorRepository extends JpaRepository<SystemVendor, Long> {

    Optional<SystemVendor> findByVendorCode(String vendorCode);

    Optional<SystemVendorView> findByStatus(Status status);

}
