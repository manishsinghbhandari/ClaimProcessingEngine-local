package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.ClaimBundleLogEntity;
import io.coredge.health.claims.db.repositories.ClaimBundleLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class ClaimBundleLogService {

  @Autowired private ClaimBundleLogRepository bundleLogRepository;

    @Transactional
    public Long saveClaimBundle(ClaimBundleLogEntity logEntity) {
        return bundleLogRepository.save(logEntity).getId();
    }

    public Long CountClaimId(UUID claimId) {
        return bundleLogRepository.countClaimId(claimId);
    }
}
