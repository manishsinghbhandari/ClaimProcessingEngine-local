package io.coredge.health.claims.models.assignments;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserTaskSummary {
    private Long userId;
    private String username;

    private Long completedTasks;
    private Long pendingTasks;
    private Long totalRecords;
}
