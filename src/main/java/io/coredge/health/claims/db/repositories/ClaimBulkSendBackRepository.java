package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.models.ClaimBulkSendBackLogs;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClaimBulkSendBackRepository extends MongoRepository<ClaimBulkSendBackLogs, String> {
}
