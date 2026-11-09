package io.coredge.health.claims.dto.utilization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PatientUtilizationResponseDto {
    private String crId;
    private String shaCode;

    private LocalDateTime policyStartDate;
    private LocalDateTime policyEndDate;

//    private List<PatientUtilizationItemDto> preauthorization;
    private List<PatientUtilizationItemDto> claim;
}
