package io.coredge.health.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SchemeMemberDTO {
    private Long scheme_detail_id;
    private String patient_id;
    private String member_number;
    private String principal_member_number;
    private Long job_group_id;
    private Long status_id;
    private Long created_by;
    private Long updated_by;
    private Long member_relation_id;
}