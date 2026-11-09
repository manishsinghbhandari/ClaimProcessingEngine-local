package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.AdminLevelOne;
import io.coredge.health.claims.db.entity.AdminLevelPopulation;
import io.coredge.health.claims.db.entity.CountyPopulation;
import io.coredge.health.claims.db.repositories.AdminLevelPopulationRepository;
import io.coredge.health.claims.db.repositories.AdminLevelOneRepository;

import java.util.stream.Collectors;

import io.coredge.health.claims.exception.app.DataValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CountyPopulationService {

    private final AdminLevelPopulationRepository adminLevelPopulationRepository;
    private final AdminLevelOneRepository adminLevelOneRepository;
    
    // No longer using a single county admin level code as we now have individual county codes

    // Create or Update county population in admin_level_populations
    public AdminLevelPopulation saveOrUpdateCountyPopulation(String countyName, Long population, String month) {
        // Find county admin level by name (exact match) or by case-insensitive search
        AdminLevelOne countyEntity = findCountyByName(countyName);
        if (countyEntity == null) {
            throw new DataValidationException("County not found with name: " + countyName);
        }
        
        // Check if record already exists
        Optional<AdminLevelPopulation> existing = adminLevelPopulationRepository
                .findByMonthAndAdminLevelEntity(month, countyEntity);
        
        AdminLevelPopulation adminLevelPopulation;
        if (existing.isPresent()) {
            // Update existing record
            adminLevelPopulation = existing.get();
            adminLevelPopulation.setPopulation(population);
            log.info("Updating county population for {} (code: {}) in {}: {}", 
                    countyEntity.getName(), countyEntity.getCode(), month, population);
        } else {
            // Create new record
            adminLevelPopulation = AdminLevelPopulation.builder()
                    .adminLevelEntity(countyEntity)
                    .population(population)
                    .month(month)
                    .build();
            log.info("Creating new county population for {} (code: {}) in {}: {}", 
                    countyEntity.getName(), countyEntity.getCode(), month, population);
        }
        
        return adminLevelPopulationRepository.save(adminLevelPopulation);
    }

    // Get by County - returns AdminLevelPopulation (new method for new automation)
    public Optional<AdminLevelPopulation> getAdminLevelPopulation(String month, String county) {
        AdminLevelOne countyEntity = findCountyByName(county);
        if (countyEntity == null) {
            return Optional.empty();
        }
        return adminLevelPopulationRepository.findByMonthAndAdminLevelEntity(month, countyEntity);
    }

    // Get All County Populations for a specific month (new method for new automation)
    public List<AdminLevelPopulation> getAdminLevelPopulationsByMonth(String month) {
        return adminLevelPopulationRepository.findAllByMonth(month);
    }

    // Delete by County
    public void deleteCountyPopulation(String county, String month) {
        AdminLevelOne countyEntity = findCountyByName(county);
        if (countyEntity == null) {
            log.warn("Cannot delete county population - county not found: {}", county);
            return;
        }
        int deletedCount = adminLevelPopulationRepository.deleteByAdminLevelEntityAndMonth(countyEntity, month);
        log.info("Deleted {} county population records for {} (code: {}) in {}", 
                deletedCount, countyEntity.getName(), countyEntity.getCode(), month);
    }

    // Backward compatibility methods for old PHCClaimService
    // These convert AdminLevelPopulation to CountyPopulation for legacy code
    public Optional<CountyPopulation> getCountyPopulation(String month, String county) {
        AdminLevelOne countyEntity = findCountyByName(county);
        if (countyEntity == null) {
            return Optional.empty();
        }
        return adminLevelPopulationRepository.findByMonthAndAdminLevelEntity(month, countyEntity)
                .map(this::convertToCountyPopulation);
    }

    public List<CountyPopulation> getCountyPopulationsByMonth(String month) {
        return adminLevelPopulationRepository.findAllByMonth(month).stream()
                .map(this::convertToCountyPopulation)
                .collect(Collectors.toList());
    }

    // Helper method to convert AdminLevelPopulation to CountyPopulation for backward compatibility
    private CountyPopulation convertToCountyPopulation(AdminLevelPopulation adminLevelPopulation) {
        CountyPopulation countyPopulation = new CountyPopulation();
        countyPopulation.setCounty(adminLevelPopulation.getAdminLevelEntity().getName());
        countyPopulation.setPopulation(adminLevelPopulation.getPopulation());
        countyPopulation.setMonth(adminLevelPopulation.getMonth());
        return countyPopulation;
    }
    
    /**
     * Find a county by name using various matching strategies:
     * 1. Exact match
     * 2. Case-insensitive match
     * 3. Partial match (contains)
     * 
     * @param countyName The name of the county to find
     * @return The AdminLevelOne entity if found, null otherwise
     */
    private AdminLevelOne findCountyByName(String countyName) {
        if (countyName == null || countyName.trim().isEmpty()) {
            return null;
        }
        
        // Try exact match first
        Optional<AdminLevelOne> exactMatch = adminLevelOneRepository.findByName(countyName);
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }
        
        // Try case-insensitive match
        String normalizedName = countyName.trim();
        List<AdminLevelOne> allCounties = adminLevelOneRepository.findAll();
        
        // First try exact match with case-insensitive comparison
        for (AdminLevelOne county : allCounties) {
            if (county.getName() != null && county.getName().equalsIgnoreCase(normalizedName)) {
                return county;
            }
        }
        
        // Then try contains match
        for (AdminLevelOne county : allCounties) {
            if (county.getName() != null && 
                (county.getName().toUpperCase().contains(normalizedName.toUpperCase()) || 
                 normalizedName.toUpperCase().contains(county.getName().toUpperCase()))) {
                return county;
            }
        }
        
        // No match found
        return null;
    }
}

