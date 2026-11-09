package io.coredge.health.claims.dto.eligibility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EligibilityResponseDTO {
    private Integer requestIdType;
    private String requestIdNumber;
    private String memberCrNumber;
    private String principalMemberCrNumber;
    private String fullName;
    private String gender;
    private Date dateOfBirth;
    private String memberType;
    private LocalDate coverageStartDate;
    private LocalDate coverageEndDate;
    private Integer status;
    private String message;
    private String reason;
    private String possibleSolution;
    private String coverageType;
    private String primaryContributor;
    private EmployerDetailsDTO employerDetails;
    private List<EligibilityBenefitDTO> benefits;
}