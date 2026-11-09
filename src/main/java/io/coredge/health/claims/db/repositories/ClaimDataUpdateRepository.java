package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.models.ClaimUpdateLogs;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimDataUpdateRepository extends MongoRepository<ClaimUpdateLogs, String> {

    Optional<List<ClaimUpdateLogs>> findByClaimIdOrderByUpdateTimeDesc(String claimId);
 }
