package io.coredge.health.claims.service;


import io.coredge.health.claims.db.entity.CountyPopulation;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.phccalculation.PHCClaimsCountyProviderCountDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PHCClaimAsyncService {

    @Autowired
    private InsuranceClaimsRepository insuranceClaimsRepository;

    @Autowired
    private  CountyPopulationService countyPopulationService;

    @Autowired
    private PHCClaimService phcClaimService;

    public Optional<List<PHCClaimsCountyProviderCountDTO>> findProvidersGroupByCounty(String monthName) {
        LocalDateTime[] dateRange = Helper.getStartAndEndDateTime(Helper.decodeValue(monthName));

        // Fetch all distinct county names
        List<String> countyNames = insuranceClaimsRepository.findDistinctCountyNames();
        if (countyNames.isEmpty()) {
            return Optional.empty();
        }

        // Fetch provider count per county
        Map<String, Long> countyProviderCountMap = getDistinctProviderCountByCounty(dateRange[0], dateRange[1], ExtensionStateCodeEnum.APPROVED);

        // Fetch county populations
        List<CountyPopulation> countyPopulations = countyPopulationService.getCountyPopulationsByMonth(monthName);
        Map<String, Long> countyPopulationMap = countyPopulations.stream()
                .collect(Collectors.toMap(CountyPopulation::getCounty, CountyPopulation::getPopulation, (a, b) -> a));

        // Map the data into the DTO
        List<PHCClaimsCountyProviderCountDTO> result = countyNames.stream()
                .map(county -> new PHCClaimsCountyProviderCountDTO(
                        county,
                        countyPopulationMap.getOrDefault(county, 0L), // Default to 0 if not found
                        countyProviderCountMap.getOrDefault(county, 0L) // Default to 0 if not found
                ))
                .collect(Collectors.toList());

        // Run async method
        phcClaimService.checkAndRunAsyncMethod(monthName);

        return Optional.of(result);
    }


    public Map<String, Long> getDistinctProviderCountByCounty(LocalDateTime startDate, LocalDateTime endDate, ExtensionStateCodeEnum status) {
        List<Object[]> results = insuranceClaimsRepository.countDistinctProvidersByCounty(startDate, endDate, status);

        return results.stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }

}
