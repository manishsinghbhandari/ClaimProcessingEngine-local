package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.phccalculation.PHCClaimsSummary;
import io.coredge.health.claims.models.phccalculation.PHCClaimsSummaryCountry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PHCClaimRepositoryCountry extends MongoRepository<PHCClaimsSummaryCountry, String> {

    Optional<PHCClaimsSummaryCountry> findByClaimId(String claimId);

    Optional<List<PHCClaimsSummaryCountry>> findByMonthAndStatus(String month, ExtensionStateCodeEnum status);

}
