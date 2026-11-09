package io.coredge.health.claims.dto.eligibility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmployerDetailsDTO {
    private String employerName;
    private String jobGroup;
}