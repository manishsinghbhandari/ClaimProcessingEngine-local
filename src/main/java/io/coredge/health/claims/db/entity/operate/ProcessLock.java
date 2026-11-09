package io.coredge.health.claims.db.entity.operate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "process_lock")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessLock {

    @Id
    @Column(name = "claim_id")
    private String claimId;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt = LocalDateTime.now();

    @Column(name = "locked_by")
    private String lockedBy;
}