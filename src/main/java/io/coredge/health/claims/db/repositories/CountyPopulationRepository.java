package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.CountyPopulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CountyPopulationRepository extends MongoRepository<CountyPopulation, String> {

    List<CountyPopulation> findAllByMonth(String month);

    Optional<CountyPopulation> findAllByMonthAndCounty(String month, String county);

    int deleteByCountyAndMonth(String county, String month);
}
