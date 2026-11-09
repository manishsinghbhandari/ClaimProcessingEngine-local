package io.coredge.health.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SurveillanceLogResponse {
    private String previousStatus;
    private String reason;
    private String status;
    private LocalDateTime creationDate;
    private LocalDateTime lastModifiedDate;
}
