package io.coredge.health.claims.models.assignments;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserAssignmentResponse {
    private Long userId;
    private String username;
    private TaskDetails taskDetails;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TaskDetails {
        private Long taskAssigned = 0L;
        private Long taskPending = 0L;
        private Long taskCompleted = 0L;
    }
}
