package io.coredge.health.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemeMemberListDTO {
    private Long id;
    private Long schemeDetailId;
    private String patientId;
    private String memberNumber;
    private String principalMemberNumber;
    private Long jobGroupId;
    private Long memberRelationId;
    private String memberNames;
    private String jobGroup;
    private String nationalId;
    private String householdNo;
}