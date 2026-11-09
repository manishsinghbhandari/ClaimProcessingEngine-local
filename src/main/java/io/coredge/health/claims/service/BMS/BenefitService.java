package io.coredge.health.claims.service.BMS;

import io.coredge.health.claims.db.entity.BMS.Benefit;
import io.coredge.health.claims.db.repositories.BMS.BenefitRepository;
import io.coredge.health.claims.exception.app.BenefitNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing Benefit entities.
 * Provides CRUD operations and custom queries for benefits in the BMS.
 * Author: Reinhard Odhiambo
 */
@Service
public class BenefitService {

    // Inject the BenefitRepository for database operations
    private final BenefitRepository benefitRepository;


    /**
     * Constructor for dependency injection.
     *
     * @param benefitRepository The repository for Benefit entities.
     */
    public BenefitService(BenefitRepository benefitRepository) {
        this.benefitRepository = benefitRepository;
    }

    /**
     * Retrieve all benefits from the database.
     *
     * @return List of Benefit entities.
     */
    public List<Benefit> getAllBenefits() {
        return benefitRepository.findAll();
    }

    /**
     * Find a benefit by its ID.
     *
     * @param id The benefit ID.
     * @return Optional containing the Benefit if found.
     */
    public Benefit getBenefitById(Long id) {
        return benefitRepository.findById(id).orElseThrow(() -> new BenefitNotFoundException("Benefit not found for ID: " + id));
    }

    /**
     * Find a benefit by its code.
     *
     * @param code The benefit code.
     * @return Optional containing the Benefit if found.
     */
    public Benefit getBenefitByCode(String code) {
        return benefitRepository.findByCode(code).orElseThrow(() -> new BenefitNotFoundException("Benefit not found for Code: " + code));
    }

    /**
     * Find benefits by their name.
     *
     * @param name The benefit name.
     * @return List of Benefit entities matching the name.
     */
    public List<Benefit> getBenefitsByName(String name) {
        return benefitRepository.findByName(name);
    }

}