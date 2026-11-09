package io.coredge.health.claims.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.payments.entity.PaymentAudit;
import io.coredge.health.claims.payments.enums.PaymentsEnum;
import io.coredge.health.claims.payments.repository.PaymentAuditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PaymentAuditService {

    private final PaymentAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public PaymentAuditService(PaymentAuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInitialRequest(PaymentsEnum.AuditEventType type, PaymentsEnum.SystemType source, String claimId,
            String batchId, PaymentsEnum.PaymentStatus status, Object payload) {
        PaymentAudit audit = new PaymentAudit();
        audit.setEventType(type);
        audit.setSourceSystem(source);
        audit.setTargetSystem(PaymentsEnum.SystemType.PAYER);
        audit.setClaimId(claimId);
        audit.setBatchId(batchId);
        audit.setForwardStatus(status);
        audit.setRequestPayload(toJson(payload));
        auditRepository.save(audit);
        log.info("Logged initial {} payment request from {}, ClaimId: {}, BatchId: {}, Status: {}", type, source,
                claimId,
                batchId, status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(PaymentsEnum.AuditEventType type, PaymentsEnum.SystemType source,
            PaymentsEnum.SystemType target,
            PaymentsEnum.PaymentStatus status, String claimId, String batchId, Object reqPayload,
            Object resPayload, String responseStatus, String auditStatus, String remarks) {
        PaymentAudit audit = new PaymentAudit();
        audit.setEventType(type);
        audit.setSourceSystem(source);
        audit.setTargetSystem(target);
        audit.setForwardStatus(status);
        audit.setClaimId(claimId);
        audit.setBatchId(batchId);
        audit.setRequestPayload(toJson(reqPayload));
        audit.setResponsePayload(toJson(resPayload));
        audit.setResponseStatus(responseStatus);
        audit.setStatus(auditStatus);
        audit.setRemarks(remarks);
        try {
            auditRepository.save(audit);
        } catch (Exception e) {
            log.error("Failed to persist audit log for event: {} and claim: {}", type, claimId, e);
        }
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return "null";
        }
        // Handle String specially to ensure it's quoted as JSON string
        if (payload instanceof String) {
            try {
                // Directly quote the string - this is the common case
                return objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                log.error("Failed to serialize string for audit", e);
                return "\"Serialization Error\"";
            }
        }
        // For non-strings, let Jackson handle it
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize payload for audit", e);
            return "\"Serialization Error\"";
        }
    }
}
