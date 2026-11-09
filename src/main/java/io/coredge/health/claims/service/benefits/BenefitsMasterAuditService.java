package io.coredge.health.claims.service.benefits;


import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.benefits.BenefitsMasterLog;
import io.coredge.health.claims.db.repositories.benefits.BenefitsMasterLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class BenefitsMasterAuditService {

    private final BenefitsMasterLogRepository logRepository;

    public void saveLog(Long benefitMasterId, String prevJson, String currJson) {
      BenefitsMasterLog log = new BenefitsMasterLog();
        log.setBenefitMasterId(benefitMasterId);
        log.setPreviousRecord(prevJson);
        log.setCurrentRecord(currJson);

        log.setCreatedBy(HealthIdContextHolder.userId());
        log.setLastModifiedBy(HealthIdContextHolder.userId());
        logRepository.save(log);
    }

}

