package io.coredge.health.claims.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public class StatusBase extends ApproverBase {
  @Column(name = "status")
  private String status;

  @Column(name = "state")
  private String state;
}
