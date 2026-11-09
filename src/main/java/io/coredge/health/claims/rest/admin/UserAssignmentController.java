package io.coredge.health.claims.rest.admin;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.assignments.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.coredge.health.claims.constants.Constants.AdminEndPoints.*;

@RequestMapping(ADMIN_ENDPOINT_PREFIX)
public interface UserAssignmentController {


    //    @PreAuthorize("hasRole('admin')")
//    @RequiresPermission(resource = "user_manage", action = {"view"})
    @GetMapping(USER_API_ALL_BY_RESOURCE)
    ResponseEntity<CollectionModel<EntityModel<Map<String, List<UserAssignmentResponse>>>>> getUsersGroupedByResourceNames(@RequestParam List<String> resourceName, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

    //    @PreAuthorize("hasRole('admin')")
//    @RequiresPermission(resource = "user_manage", action = {"view"})
    @PostMapping(USER_API_ASSIGN_TASKS)
    ResponseEntity<Map<String, Long>> assignTasks(@RequestBody UserAssignmentDTO userAssignmentDTO);

    //    @PreAuthorize("hasRole('admin')")
//    @RequiresPermission(resource = "user_manage", action = {"view"})
    @GetMapping(USER_API_REPORTING)
    ResponseEntity<PagedModel<EntityModel<UserReportingDTO>>> userReporting(@RequestParam(defaultValue = "preauthorization") String resourceType, @RequestParam(defaultValue = "MM-dd-YYYY") String startDate, @RequestParam(defaultValue = "0") String endDate, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "id") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

    @GetMapping(USER_API_ASSIGN_TASKS)
    ResponseEntity<Map<String, Long>> assignTasksToCurrentUser(@PathVariable("use") String use  ,
                                                               @PathVariable("status") ExtensionStateCodeEnum status ,
                                                               @RequestParam(required = false) String code );

    @GetMapping(REVIEWER_ACTIVITY)
    ResponseEntity<PagedModel<EntityModel<UserReporting>>> getWorkflowReviewReport(
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lastActionTime,
            @RequestParam(defaultValue = "user.username") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );

    @PatchMapping(RELEASE_UNACTIONED_TASK)
    ResponseEntity<ReleaseResultDTO> releaseUnactionedTasks(@PathVariable ("userId") Long userId);

}