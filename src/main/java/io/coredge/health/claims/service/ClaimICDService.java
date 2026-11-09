package io.coredge.health.claims.service;

import io.coredge.health.claims.db.repositories.ClaimICDRelationRepository;
import io.coredge.health.claims.models.redis.ClaimICDRelation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ClaimICDService {

    @Autowired
    private ClaimICDRelationRepository claimICDRelationRepository;

    public Optional<List<ClaimICDRelation>> getClaimsByCommaSeparatedIds(String commaSeparatedIds) {
        List<String> claimIds = Arrays.asList(commaSeparatedIds.split(","));
        return claimICDRelationRepository.findByClaimIdIn(claimIds);
    }

    public List<ClaimICDRelation> getClaimsIcdByClaimIds(List<String> claimIds) {
        List<ClaimICDRelation> response = findByClaimIdInBatches(claimIds);
        if (response.isEmpty()) {
            log.warn("Error while fetching claims ICD mappings");
            throw new RuntimeException("Error while fetching claims ICD mappings");
        }
        return response;
    }

    public List<ClaimICDRelation> findByClaimIdInBatches(List<String> claimIds) {
        List<ClaimICDRelation> result = new ArrayList<>();
        int batchSize = 10000;

        for (int i = 0; i < claimIds.size(); i += batchSize) {
            List<String> batch = claimIds.subList(i, Math.min(i + batchSize, claimIds.size()));
            Optional<List<ClaimICDRelation>> batchResult = claimICDRelationRepository.findByClaimIdIn(batch);
            batchResult.ifPresent(result::addAll);
        }
        return result;
    }

}
