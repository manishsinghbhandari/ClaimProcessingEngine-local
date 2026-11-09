package io.coredge.health.claims.mapper;

import io.coredge.health.claims.models.assignments.UserAssignmentResponse;
import io.coredge.health.claims.models.assignments.UserTaskSummary;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public class UserAssignmentMapper {

    public static List<UserAssignmentResponse> convertToUserAssignmentResponse(List<UserTaskSummary> userTaskSummaries) {
        return userTaskSummaries.stream().map(s -> UserAssignmentResponse.builder()
                .userId(s.getUserId())
                .username(s.getUsername())
                .taskDetails(UserAssignmentResponse.TaskDetails.builder().
                        taskAssigned(s.getTotalRecords()).
                        taskPending(s.getPendingTasks()).
                        taskCompleted(s.getCompletedTasks())
                        .build()).build())
                .collect(Collectors.toList());
    }
}
