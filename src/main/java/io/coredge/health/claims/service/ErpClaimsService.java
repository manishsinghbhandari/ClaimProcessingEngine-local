package io.coredge.health.claims.service;

import io.coredge.health.claims.client.ErpClient;
import io.coredge.health.claims.db.entity.ErpClaims;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.ErpClaimsRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class ErpClaimsService {

    private final ErpClaimsRepository repository;
    @Autowired
    private ClaimExtensionService extensionService;

    @Autowired
    private ErpClient erpClient;

    @Autowired
    private PostOfficeService postOfficeService;

    @Autowired
    private InsuranceClaimsRepository insuranceClaimsRepository;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private BenefitsMasterRepository benefitsRepository;

    @Autowired
    public ErpClaimsService(ErpClaimsRepository repository) {
        this.repository = repository;
    }

    public ErpClaims createClaimFundScheme(ErpClaims claimFundScheme) {
        return repository.save(claimFundScheme);
    }

    public List<ErpClaims> getAllClaimFundSchemes() {
        return repository.findAll();
    }

    public Optional<ErpClaims> getClaimFundSchemeById(Long id) {
        return repository.findById(id);
    }

    public void saveErpClaims(ErpClaims erpClaims) {
        if (erpClaims != null) {
            repository.save(erpClaims);
        }
    }

    public ErpClaims findByClaimNo(String claimNo){
        return repository.findByClaimNo(claimNo).orElse(null);
    }


    public List<ErpClaims> findByParentClaimNo(String claimNo){
        return repository.findByParentClaimNo(claimNo).orElse(null);
    }

    public Boolean existByClaimNo(String claimNo){
        return repository.existsByClaimNo(claimNo).orElse(false);
    }

    public void deleteClaimFundScheme(Long id) {
        repository.deleteById(id);
    }

    public Boolean isExistsByClaimNoHttpCallStatus(String claimNo,  Collection<String> httpCallStatuses) {
        return repository.existsByClaimNoAndHttpCallStatusIn(claimNo, httpCallStatuses).orElse(false);
    }

    public Boolean isExistsByClaimNoAndPaymentStatus(String claimNo,  String paymentStatus) {
        return repository.existsByClaimNoAndPaymentStatus(claimNo, paymentStatus).orElse(false);
    }
}
