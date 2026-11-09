package io.coredge.health.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SchemeMemberWithLimitsDTO {
    private Long id;
    private String patientId;
    private String memberNumber;
    private String principalMemberNumber;
    private Long schemeDetailId;
    private String birthDate;
    private String gender;
    private String name;
    private MemberRelationDTO relation;
    private List<SchemeMemberLimitDTO> limits;
    private String jobGroup;
    private String nationalId;
    private String householdNo;
    private String crNumber;
}