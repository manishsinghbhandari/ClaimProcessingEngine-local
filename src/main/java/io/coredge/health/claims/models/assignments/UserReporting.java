package io.coredge.health.claims.models.assignments;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserReporting {

    private Long userId;
    private String username;
    private Long totalRecords;
    private Long totalTasksPending;   // Unactioned
    private Long totalTaskReleased;
    private Long totalTasksCompleted; // Actioned
    private LocalDateTime lastActionTime;
}


