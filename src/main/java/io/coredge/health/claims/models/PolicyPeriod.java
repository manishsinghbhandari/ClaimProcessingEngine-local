package io.coredge.health.claims.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PolicyPeriod {
    private LocalDate startDate;
    private LocalDate endDate;
    private String coverageType;
    private String employer;
    private String jobGroup;
}
