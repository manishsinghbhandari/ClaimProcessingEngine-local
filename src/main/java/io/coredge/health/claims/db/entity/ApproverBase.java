package io.coredge.health.claims.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public class ApproverBase extends AuditableBase<String> {

    @Column(name = "approver_1")
    private String approver1;

    @Column(name = "approver_2")
    private String approver2;

    @Column(name = "approver_3")
    private String approver3;

}
