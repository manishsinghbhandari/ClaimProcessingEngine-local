package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.phccalculation.CSV_PHCClaimSummary;
import io.coredge.health.claims.models.phccalculation.PHCClaimsSummary;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PHCClaimRepository extends MongoRepository<PHCClaimsSummary, String> {

    Optional<PHCClaimsSummary> findByClaimId(String claimId);

    Optional<List<PHCClaimsSummary>> findByCountyNameAndMonthAndProviderId(String countyName, String month, Long providerId);

    boolean existsByCountyNameAndMonthAndProviderId(String countyName, String month, Long providerId);

    Optional<List<PHCClaimsSummary>> findByMonthAndStatus(String month, ExtensionStateCodeEnum status);

    boolean existsByMonthAndStatus(String monthName, ExtensionStateCodeEnum extensionStateCodeEnum);

    boolean existsByMonth(String monthName);

    @Query("{ 'month' : ?0 }")
    Optional<List<CSV_PHCClaimSummary>> findByMonth(String month);
}
