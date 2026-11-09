/**
 * Service for managing JobGroup entities.
 * Provides CRUD operations for job groups in the BMS.
 * Author: Reinhard Odhiambo
 */
package io.coredge.health.claims.service.BMS;

import io.coredge.health.claims.db.entity.BMS.JobGroup;
import io.coredge.health.claims.db.repositories.BMS.JobGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class JobGroupService {

    // Inject the JobGroupRepository for database operations
    @Autowired
    private JobGroupRepository jobGroupRepository;

    /**
     * Retrieve all job groups from the database.
     * @return List of JobGroup entities.
     */
    public List<JobGroup> findAll() {
        return jobGroupRepository.findAll();
    }

    /**
     * Find a job group by its ID.
     * @param id The job group ID.
     * @return Optional containing the JobGroup if found.
     */
    public Optional<JobGroup> findById(Long id) {
        return jobGroupRepository.findById(id);
    }
}