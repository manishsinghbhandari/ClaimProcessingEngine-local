package io.coredge.health.claims.models.assignments;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReleaseResultDTO {
    private Long userId;
    private int releasedCount;
    private Instant timestamp;
    private String message;


    // Static factory method for success with claims count
    public static ReleaseResultDTO success(Long userId, int releasedCount, int updatedClaimsCount) {
        return ReleaseResultDTO.builder()
                .userId(userId)
                .releasedCount(releasedCount)
                .timestamp(Instant.now())
                .message(String.format(
                        "Successfully released %d pending tasks and updated %d claims",
                        releasedCount, updatedClaimsCount))
                .build();
    }

    // Static factory method for no tasks case
    public static ReleaseResultDTO noTasks(Long userId) {
        return ReleaseResultDTO.builder()
                .userId(userId)
                .releasedCount(0)
                .timestamp(Instant.now())
                .message("No pending tasks found for release")
                .build();
    }
}
