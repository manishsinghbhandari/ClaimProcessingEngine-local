package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.models.redis.ClaimICDRelation;
import org.bson.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClaimICDRelationRepository extends MongoRepository<ClaimICDRelation, String> {

//    @Query("{'claimId': ?0}")
    Optional<List<ClaimICDRelation>> findByClaimId(String claimId);

    @Query(value = "{ 'claimId': ?0 }", fields = "{ 'diagnosis.icd_code': 1 }")
    Optional<List<String>> findDistinctIcdCodesByClaimId(String claimId);

    @Query(value = "{ 'claimId': { $in: ?0 } }", fields = "{ 'claimId': 1, 'diagnosis.icd_code': 1 }")
    List<ClaimICDRelation> findDistinctIcdCodesByClaimIds(List<String> claimIds);

    // Find by multiple claim IDs
    @Query("{ 'claimId': { $in: ?0 } }")
    Optional<List<ClaimICDRelation>> findByClaimIdIn(List<String> claimIds);

 }
