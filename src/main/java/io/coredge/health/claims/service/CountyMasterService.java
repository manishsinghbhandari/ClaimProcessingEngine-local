package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.AdminLevelOne;
import io.coredge.health.claims.db.entity.UserCountyMapping;
import io.coredge.health.claims.db.repositories.AdminLevelOneRepository;
import io.coredge.health.claims.db.repositories.ProviderRepository;
import io.coredge.health.claims.db.repositories.UserCountyMappingRepository;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.models.master.CountyMasterResponse;
import io.coredge.health.claims.models.master.CountyOptionResponse;
import io.coredge.health.claims.models.master.UserCountyAssignmentRequest;
import io.coredge.health.claims.models.master.UserCountyAssignmentResponse;
import io.coredge.health.claims.models.master.UserCountySummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CountyMasterService {

    @Autowired
    private ProviderRepository providerRepository;
    
    @Autowired
    private UserCountyMappingRepository userCountyMappingRepository;
    
    @Autowired
    private AdminLevelOneRepository adminLevelOneRepository;
    
    @Autowired
    private PagedResourcesAssembler<UserCountySummaryResponse> pagedResourcesAssembler;
    /**
     * Fetches all distinct counties from Provider entity
     * @return List of CountyMasterResponse with county names
     */
    public List<CountyMasterResponse> fetchAllCounties() {
        List<String> counties = providerRepository.findAllByCounty();
        
        if (counties == null || counties.isEmpty()) {
            throw new DataNotFoundException();
        }
        
        return counties.stream()
                .filter(county -> county != null && !county.isEmpty())
                .map(county -> CountyMasterResponse.builder().name(county).build())
                .collect(Collectors.toList());
    }

    /**
     * Fetch all counties with identifiers (id, name, code) from AdminLevelOne table
     * Useful for building selection lists in the UI when assigning mappings
     */
    public List<CountyOptionResponse> fetchAllCountyOptions() {
        List<AdminLevelOne> all = adminLevelOneRepository.findAll();
        if (all == null || all.isEmpty()) {
            throw new DataNotFoundException();
        }
        return all.stream()
                .map(alo -> CountyOptionResponse.builder()
                        .id(alo.getId())
                        .name(alo.getName())
                        .code(alo.getCode())
                        .build())
                .collect(Collectors.toList());
    }

    // --------------------------------< User County Assignment Methods > --------------------------------

    /**
     * Assign multiple counties to a user.
     * Performs efficient batch operations:
     * - Deactivates old mappings not in request
     * - Reactivates existing inactive mappings
     * - Creates new mappings as needed
     */
    @Transactional
    public List<UserCountyAssignmentResponse> assignCountiesToUser(UserCountyAssignmentRequest request) {
        String username = request.getUsername();
        List<Long> requestedCountyIds = request.getCountyIds();

        log.info("Assigning {} counties to user: {}", requestedCountyIds.size(), username);

        // Validate all county IDs exist
        List<AdminLevelOne> validCounties = adminLevelOneRepository.findAllById(requestedCountyIds);
        if (validCounties.size() != requestedCountyIds.size()) {
            throw new BadRequestException("One or more county IDs are invalid");
        }

        // Fetch all existing mappings for this user
        List<UserCountyMapping> existingMappings = userCountyMappingRepository.findByUsername(username);

        // Split existing mappings into active and inactive sets for quick lookup
        Map<Long, UserCountyMapping> activeMap = existingMappings.stream()
                .filter(UserCountyMapping::getIsActive)
                .collect(Collectors.toMap(UserCountyMapping::getAdminLevelOneId, m -> m));
        Map<Long, UserCountyMapping> inactiveMap = existingMappings.stream()
                .filter(m -> !m.getIsActive())
                .collect(Collectors.toMap(UserCountyMapping::getAdminLevelOneId, m -> m));

        // Determine which to deactivate
        List<Long> countiesToDeactivate = activeMap.keySet().stream()
                .filter(id -> !requestedCountyIds.contains(id))
                .collect(Collectors.toList());

        if (!countiesToDeactivate.isEmpty()) {
            userCountyMappingRepository.deactivateByUsernameAndCountyIds(username, countiesToDeactivate);
            log.info("Deactivated {} county assignments for user: {}", countiesToDeactivate.size(), username);
        }

        // Build list of mappings to activate or create
        List<UserCountyMapping> mappingsToSave = new ArrayList<>();
        List<UserCountyMapping> finalMappings = new ArrayList<>();

        for (Long countyId : requestedCountyIds) {
            if (activeMap.containsKey(countyId)) {
                // Already active — include in final result
                finalMappings.add(activeMap.get(countyId));
            } else if (inactiveMap.containsKey(countyId)) {
                // Reactivate existing inactive mapping
                UserCountyMapping mapping = inactiveMap.get(countyId);
                mapping.setIsActive(true);
                mappingsToSave.add(mapping);
                finalMappings.add(mapping);
                log.debug("Reactivating county {} for user {}", countyId, username);
            } else {
                // Create new mapping
                UserCountyMapping newMapping = UserCountyMapping.builder()
                        .username(username)
                        .adminLevelOneId(countyId)
                        .isActive(true)
                        .build();
                mappingsToSave.add(newMapping);
                finalMappings.add(newMapping);
                log.debug("Creating new county mapping {} for user {}", countyId, username);
            }
        }

        // Save new/reactivated mappings in batch
        if (!mappingsToSave.isEmpty()) {
            userCountyMappingRepository.saveAll(mappingsToSave);
            log.info("Saved {} new/reactivated mappings for user: {}", mappingsToSave.size(), username);
        }

        log.info("Successfully assigned {} counties to user: {}", finalMappings.size(), username);

        return finalMappings.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all active county assignments for a user.
     */
    public UserCountySummaryResponse getUserCountyAssignments(String username) {
        log.info("Fetching county assignments for user: {}", username);

        List<UserCountyMapping> activeMappings = userCountyMappingRepository.findByUsernameAndIsActive(username, true);

        if (activeMappings.isEmpty()) {
            return UserCountySummaryResponse.builder()
                    .username(username)
                    .assignedCounties(Collections.emptyList())
                    .hasUnrestrictedAccess(true)
                    .build();
        }

        List<UserCountySummaryResponse.CountyInfo> countyInfoList = activeMappings.stream()
                .map(m -> {
                    Long countyId = m.getAdminLevelOneId();
                    AdminLevelOne county = m.getAdminLevelOne();
                    if (county == null && countyId != null) {
                        county = adminLevelOneRepository.findById(countyId).orElse(null);
                    }
                    return UserCountySummaryResponse.CountyInfo.builder()
                            .countyId(countyId)
                            .countyName(county != null ? county.getName() : null)
                            .countyCode(county != null ? county.getCode() : null)
                            .build();
                })
                .collect(Collectors.toList());

        return UserCountySummaryResponse.builder()
                .username(username)
                .assignedCounties(countyInfoList)
                .hasUnrestrictedAccess(false)
                .build();
    }

    /**
     * Returns list of active county IDs assigned to a user.
     */
    public List<Long> getUserAssignedCountyIds(String username) {
        return userCountyMappingRepository.findCountyIdsByUsername(username);
    }

    /**
     * Returns true if user has unrestricted access (no county mappings).
     */
    public boolean hasUnrestrictedAccess(String username) {
        return !userCountyMappingRepository.existsByUsernameAndIsActive(username, true);
    }

    /**
     * Get all users with their county assignments in paginated format.
     * Returns only users who have active county mappings.
     */
    public CollectionModel<EntityModel<UserCountySummaryResponse>> getAllUsersWithCountyAssignments(Pageable pageable) {
        log.info("Fetching all users with county assignments, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        // Get all distinct usernames with active county assignments
        List<String> allUsernames = userCountyMappingRepository.findDistinctUsernamesWithActiveAssignments();
        
        if (allUsernames.isEmpty()) {
            log.info("No users found with active county assignments");
            Page<UserCountySummaryResponse> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            return pagedResourcesAssembler.toModel(emptyPage);
        }

        // Calculate pagination boundaries
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allUsernames.size());
        
        // Get paginated usernames
        List<String> paginatedUsernames = allUsernames.subList(start, end);
        
        // Build response for each user in the page
        List<UserCountySummaryResponse> userSummaries = paginatedUsernames.stream()
                .map(this::getUserCountyAssignments)
                .collect(Collectors.toList());
        
        // Create Page object
        Page<UserCountySummaryResponse> page = new PageImpl<>(userSummaries, pageable, allUsernames.size());
        
        log.info("Fetched {} users with county assignments out of {} total", userSummaries.size(), allUsernames.size());
        
        return pagedResourcesAssembler.toModel(page);
    }

// --------------------------------< Helper Methods > --------------------------------

    /**
     * Convert entity to response DTO.
     */
    private UserCountyAssignmentResponse mapToResponse(UserCountyMapping mapping) {
        Long countyId = mapping.getAdminLevelOneId();
        AdminLevelOne county = mapping.getAdminLevelOne();
        // Guard against null lazy relation for newly saved entities
        if (county == null && countyId != null) {
            county = adminLevelOneRepository.findById(countyId)
                    .orElseThrow(() -> new DataNotFoundException("County not found for id: " + countyId));
        }
        return UserCountyAssignmentResponse.builder()
                .id(mapping.getId())
                .username(mapping.getUsername())
                .countyId(countyId)
                .countyName(county != null ? county.getName() : null)
                .countyCode(county != null ? county.getCode() : null)
                .createdBy(mapping.getCreatedBy())
                .createdDate(mapping.getCreationDate())
                .lastModifiedBy(mapping.getLastModifiedBy())
                .lastModifiedDate(mapping.getLastModifiedDate())
                .build();
    }
}
