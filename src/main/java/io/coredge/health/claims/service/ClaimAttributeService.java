package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.ClaimAttribute;
import io.coredge.health.claims.db.repositories.ClaimAttributeRepository;
import io.coredge.health.claims.enums.AttributeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClaimAttributeService {

    private final ClaimAttributeRepository claimAttributeRepository;

    @Transactional
    public ClaimAttribute createOrUpdateClaimAttribute(UUID claimId, String pointOfCare,
                                                       String coverageType, String employer, String jobGroup) {
        // Step 1: Retire existing ACTIVE attribute
        claimAttributeRepository.findActiveAttributeByClaimId(claimId).ifPresent(activeAttribute -> {
            activeAttribute.setStatus(AttributeStatus.RETIRED);
            claimAttributeRepository.save(activeAttribute);
        });

        // Step 2: Create new ACTIVE attribute
        ClaimAttribute newAttribute = ClaimAttribute.builder()
                .claimId(claimId)
                .pointOfCare(pointOfCare)
                .coverageType(coverageType)
                .employer(employer)
                .jobGroup(jobGroup)
                .status(AttributeStatus.ACTIVE)
                .build();

        return claimAttributeRepository.save(newAttribute);
    }

    public ClaimAttribute getActiveAttribute(UUID claimId) {
        return claimAttributeRepository.findActiveAttributeByClaimId(claimId)
                .orElse(null);
    }

    public List<ClaimAttribute> getAttributeHistory(UUID claimId) {
        return claimAttributeRepository.findByClaimId(claimId);
    }
}
