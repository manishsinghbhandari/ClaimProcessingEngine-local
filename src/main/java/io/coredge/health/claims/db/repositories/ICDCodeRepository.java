package io.coredge.health.claims.db.repositories;


import io.coredge.health.claims.models.redis.ICDCode;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ICDCodeRepository extends MongoRepository<ICDCode, String> {
    // Find ICD Code by ICD 10 Disease Code
    @Query("{'ICD 10 Disease Code': ?0}")
    Optional<ICDCode> findByIcd10DiseaseCode(String icd10DiseaseCode);

    // Find ICD Code by ICD 11 CODES
    @Query("{'ICD 11 CODES': ?0}")
    Optional<ICDCode> findByIcd11Codes(String icd11Codes);

    @Query("{'ICD 11 CODES': ?0}")
    Optional<List<ICDCode>> findByIcd11Codes(List<String> icdCodes);
}
