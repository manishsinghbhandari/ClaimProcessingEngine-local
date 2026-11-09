package io.coredge.health.claims.rest.admin;

import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.assignments.*;

import io.coredge.health.claims.service.UserAssignmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.coredge.health.claims.constants.Constants.AdminEndPoints.ADMIN_ENDPOINT_PREFIX;

@RestController
@RequestMapping(ADMIN_ENDPOINT_PREFIX)
public class UserAssignmentControllerImpl implements UserAssignmentController {

    @Autowired
    private UserAssignmentService userAssignmentService;

    @Autowired
    private InsuranceClaimsRepository insuranceClaimsRepository;


    @Override
    public ResponseEntity<CollectionModel<EntityModel<Map<String, List<UserAssignmentResponse>>>>> getUsersGroupedByResourceNames(List<String> resourceName, int page, int size, String sortBy, String direction) {
        PagedModel<EntityModel<Map<String, List<UserAssignmentResponse>>>> response = userAssignmentService.getUsersGroupedByResourceNames(resourceName, PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy)));
        long unAssignedClaims = insuranceClaimsRepository.countAllByIsAssignedFalseAndStatusAndUseInIgnoreCase(ExtensionStateCodeEnum.CLINICAL_REVIEW, resourceName);
        // Create custom headers
        HttpHeaders headers = new HttpHeaders();
        headers.add("unAssignedClaimsCount", Long.toString(unAssignedClaims));
        return ResponseEntity.ok().headers(headers).body(response);
    }

    @Override
    public ResponseEntity<Map<String, Long>> assignTasks(@RequestBody UserAssignmentDTO userAssignmentDTO) {
        return ResponseEntity.ok(userAssignmentService.processUserAssignments(userAssignmentDTO));
    }

    @Override
    public ResponseEntity<PagedModel<EntityModel<UserReportingDTO>>> userReporting(String resourceType, String startDate, String endDate, int page, int size, String sortBy, String direction) {
        return ResponseEntity.ok(userAssignmentService.getUserReporting(resourceType, startDate, endDate, PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy))));
    }

    @Override
    public ResponseEntity<Map<String, Long>> assignTasksToCurrentUser(String use , ExtensionStateCodeEnum status , String code ) {
        return ResponseEntity.ok(userAssignmentService.processAssignTaskToCurrentUser(use , status, code));
    }

    @Override
    public ResponseEntity<PagedModel<EntityModel<UserReporting>>> getWorkflowReviewReport(String userName , LocalDate lastActionTime , String sortBy, String direction, int page, int size) {
        return ResponseEntity.ok(userAssignmentService.getTodayTaskReport(userName , lastActionTime , PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy))));
    }

    @Override
    public ResponseEntity<ReleaseResultDTO> releaseUnactionedTasks(Long userId) {
        return ResponseEntity.ok(userAssignmentService.releaseUnactionedTasks(userId));
    }
}

