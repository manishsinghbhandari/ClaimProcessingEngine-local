package io.coredge.health.claims.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.db.entity.CivilServantClaimsAudit;
import io.coredge.health.claims.db.repositories.CivilServantAuditRepository;
import io.coredge.health.claims.livia.LiviaClaimRequest;
import io.coredge.health.claims.livia.LiviaPreAuthRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.coredge.health.claims.constants.Constants.ClaimStatuses.PENDING;

@Slf4j
@Service
public class CivilServantAuditService {

    @Autowired
    private CivilServantAuditRepository civilServantAuditRepository;

    ObjectMapper objectMapper = new ObjectMapper();

    public CivilServantClaimsAudit save(LiviaClaimRequest liviaClaimRequest, String liviaClaimRequestJson, String claimId) {
        try {
            List<String> codes = Optional.ofNullable(liviaClaimRequest.getLineItems())
                    .map(items -> items.stream()
                            .map(LiviaClaimRequest.LineItem::getCode)
                            .toList()
                    )
                    .orElse(List.of());

            CivilServantClaimsAudit audit = civilServantAuditRepository.findAllByClaimId(claimId).stream()
                    .findFirst()
                    .map(existingAudit -> {
                        existingAudit.setStatus("PENDING");
                        existingAudit.setInterventionCodes(codes);
                        existingAudit.setRequestBody(liviaClaimRequestJson);
                        return existingAudit;
                    })
                    .orElseGet(() -> CivilServantClaimsAudit.builder()
                            .claimId(claimId)
                            .status("PENDING")
                            .interventionCodes(codes)
                            .requestBody(liviaClaimRequestJson)
                            .build());

            return civilServantAuditRepository.save(audit);
        } catch (Exception e) {
            log.warn("Error saving civil servant audit: {}", e.getMessage(), e);
            return new CivilServantClaimsAudit();
        }
    }

    public CivilServantClaimsAudit save(LiviaPreAuthRequest liviaClaimRequest, String liviaClaimRequestJson, String claimId) {
        try {
            List<String> codes = Optional.ofNullable(liviaClaimRequest.getLineItems())
                    .map(items -> items.stream()
                            .map(LiviaPreAuthRequest.PreAuthLineItem::getCode)
                            .toList()
                    )
                    .orElse(List.of());

            CivilServantClaimsAudit audit = civilServantAuditRepository.findAllByClaimId(claimId).stream()
                    .findFirst()
                    .map(existingAudit -> {
                        existingAudit.setStatus(PENDING);
                        existingAudit.setInterventionCodes(codes);
                        existingAudit.setRequestBody(liviaClaimRequestJson);
                        return existingAudit;
                    })
                    .orElseGet(() -> CivilServantClaimsAudit.builder()
                            .claimId(claimId)
                            .status(PENDING)
                            .interventionCodes(codes)
                            .requestBody(liviaClaimRequestJson)
                            .build());

            return civilServantAuditRepository.save(audit);
        } catch (Exception e) {
            log.warn("Error saving civil servant audit: {}", e.getMessage(), e);
            return new CivilServantClaimsAudit();
        }
    }


    public void update(CivilServantClaimsAudit civilServantClaimsAudit, String liviaResponse, String claimId, String status) {
        try {
            civilServantClaimsAudit.setClaimId(claimId);
            civilServantClaimsAudit.setResponseBody(liviaResponse);
            civilServantClaimsAudit.setResponseMessage(getMessage(liviaResponse));
            civilServantClaimsAudit.setStatus(status);
            civilServantAuditRepository.save(civilServantClaimsAudit);
        } catch (Exception e) {
            log.warn("Error updating civil servant audit : {} ", e.getMessage());
        }
    }

    private String getMessage(String liviaResponse) {
        try {
            JsonNode messageNode = objectMapper.readTree(liviaResponse).get("message");

            if (messageNode == null) return "";

            if (messageNode.isTextual()) {
                return messageNode.asText();
            }

            if (messageNode.isArray()) {
                return StreamSupport.stream(messageNode.spliterator(), false)
                        .filter(JsonNode::isTextual)
                        .map(JsonNode::asText)
                        .collect(Collectors.joining(", "));
            }
        } catch (Exception e) {
            log.warn("Failed to parse error message from Livia response body", e);
        }
        return "";
    }

}
