package io.coredge.health.claims.service;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.PreAuthCancelAudit;
import io.coredge.health.claims.db.repositories.PerAuthCancelAuditRepository;
import io.coredge.health.claims.models.PreAuthCancelAuditLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PerAuthCancelAuditService {

    private final PerAuthCancelAuditRepository repository;

    public PerAuthCancelAuditService(PerAuthCancelAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void insertAuditLog(PreAuthCancelAuditLog request) {
        String source = HealthIdContextHolder.userId();
        PreAuthCancelAudit log = PreAuthCancelAudit.builder()
                .claimId(request.getClaimId())
                .source(source)
                .requester(request.getRequester())
                .requestBody(request.getRequestBodyJson())
                .response(request.getResponse())
                .errorMessage(request.getErrorMessage())
                .status(request.getStatus())
                .createdBy(source)
                .lastModifiedBy(source)
                .lastModifiedDate(LocalDateTime.now())
                .build();
        repository.save(log);
    }
}
