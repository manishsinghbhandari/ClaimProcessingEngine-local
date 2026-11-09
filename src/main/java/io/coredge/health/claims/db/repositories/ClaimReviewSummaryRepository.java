package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.models.report.ClaimReviewSummary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ClaimReviewSummaryRepository extends MongoRepository<ClaimReviewSummary, Long> {

    Optional<ClaimReviewSummary> findByFileName(String fileName);
}
