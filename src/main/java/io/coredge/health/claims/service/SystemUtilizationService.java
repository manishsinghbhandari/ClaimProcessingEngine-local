package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.SystemVendor;
import io.coredge.health.claims.db.entity.SystemVendorView;
import io.coredge.health.claims.db.repositories.SystemUtilizationChargeRepository;
import io.coredge.health.claims.db.entity.SystemUtilizationChargeView;
import io.coredge.health.claims.db.repositories.SystemVendorRepository;
import io.coredge.health.claims.enums.Status;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class SystemUtilizationService {

    private final SystemUtilizationChargeRepository systemUtilizationChargeRepository;
    private final SystemVendorRepository systemVendorRepository;

    public SystemUtilizationService(SystemUtilizationChargeRepository systemUtilizationChargeRepository, SystemVendorRepository systemVendorRepository) {
        this.systemUtilizationChargeRepository = systemUtilizationChargeRepository;
        this.systemVendorRepository = systemVendorRepository;
    }

    public Optional<SystemUtilizationChargeView> findSystemUtilizationCharge(LocalDateTime effectiveDate) {

        List<SystemUtilizationChargeView> systemUtilizationCharges = systemUtilizationChargeRepository.findByStatusOrderByEffectiveDate(Status.ACTIVE);

        return systemUtilizationCharges.stream()
                .filter(charge -> !charge.getEffectiveDate().isAfter(effectiveDate))
                .sorted(Comparator.comparing(SystemUtilizationChargeView::getEffectiveDate).reversed()) // DESC
                .findFirst();
    }

    public Optional<SystemVendor> findSystemVendorByCode(String code){
        return systemVendorRepository.findByVendorCode(code);
    }

    public Optional<SystemVendorView> findSystemVendorActive(){
        return systemVendorRepository.findByStatus(Status.ACTIVE);
    }
}
