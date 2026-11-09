package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.ReviewPriority;
import io.coredge.health.claims.db.repositories.ProviderRepository;
import io.coredge.health.claims.db.repositories.ReviewPriorityRepository;
import io.coredge.health.claims.enums.ReviewPriorityEnum;
import io.coredge.health.claims.enums.Status;
import io.coredge.health.claims.models.DropDown;
import io.coredge.health.claims.models.ReviewPriorityDTO;
import io.coredge.health.claims.service.utilization.PatientUtilizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ReviewPriorityService {

    private final PatientUtilizationService patientUtilizationService;
    private final ProviderRepository providerRepository;
    private final ReviewPriorityRepository reviewPriorityRepository;

    public ReviewPriorityService(PatientUtilizationService patientUtilizationService, ProviderRepository providerRepository, ReviewPriorityRepository reviewPriorityRepository) {
        this.patientUtilizationService = patientUtilizationService;
        this.providerRepository = providerRepository;
        this.reviewPriorityRepository = reviewPriorityRepository;
    }

    public List<DropDown> getCategories() {
        return List.of(
                new DropDown(ReviewPriorityEnum.PACKAGE_SUB_PACKAGE.name() , ReviewPriorityEnum.PACKAGE_SUB_PACKAGE.getName()),
                new DropDown(ReviewPriorityEnum.LEVEL.name() , ReviewPriorityEnum.LEVEL.getName()),
                new DropDown( ReviewPriorityEnum.OWNERSHIP.name() , ReviewPriorityEnum.OWNERSHIP.getName()),
                new DropDown(ReviewPriorityEnum.FACILITY.name() , ReviewPriorityEnum.FACILITY.getName())
        );
    }

    public List<DropDown> getSubCategories(ReviewPriorityEnum priority , String searchValue) {

        return switch (priority) {
            case PACKAGE_SUB_PACKAGE -> patientUtilizationService.getSearchBenefits(searchValue , false);
            case FACILITY -> providerRepository.findDistinctByNameDropdownIgnoreCase(searchValue);
            case LEVEL -> providerRepository.findDistinctByLevelDropdownIgnoreCase(searchValue);
            case OWNERSHIP -> providerRepository.findDistinctByFacilityAgentDropdownIgnoreCase(searchValue);
        };
    }

    public List<ReviewPriorityDTO> getPriorities() {
        List<ReviewPriority> reviewPriorities = reviewPriorityRepository.findByStatusOrderByPriorityAsc(Status.ACTIVE);
        return reviewPriorities.stream().map(this::toReviewPriorityDTO).toList();
    }

    public void updatePriorities(List<ReviewPriorityDTO> priorities) {

        try {
            reviewPriorityRepository.markAsDeleted();
            List<ReviewPriority> reviewPriorities = priorities.stream().map(this::toReviewPriority).toList();
            reviewPriorityRepository.saveAll(reviewPriorities);
        }
        catch (Exception e) {
            log.info("Error in updating priorities , with error:{}",e.getMessage());
        }
    }

    private ReviewPriorityDTO toReviewPriorityDTO(ReviewPriority reviewPriority) {
        return ReviewPriorityDTO.builder()
                .category(reviewPriority.getCategory())
                .name(reviewPriority.getName())
                .value(reviewPriority.getCategoryValue())
                .priority(reviewPriority.getPriority())
                .build();
    }

    private ReviewPriority toReviewPriority(ReviewPriorityDTO reviewPriority) {
        return ReviewPriority.builder()
                .category(reviewPriority.getCategory())
                .name(reviewPriority.getName())
                .categoryValue(reviewPriority.getValue())
                .status(Status.ACTIVE)
                .priority(reviewPriority.getPriority())
                .build();
    }
}
