package io.coredge.health.claims.models.assignments;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserReportingDTO {

    private Long userId;
    private String username;
    private Long totalTasksCompleted;
    private Long totalTasksPending;
    private Long totalRecords;
    private LocalDateTime lastAssignedTime;
    private LocalDateTime lastActionTime;
}

