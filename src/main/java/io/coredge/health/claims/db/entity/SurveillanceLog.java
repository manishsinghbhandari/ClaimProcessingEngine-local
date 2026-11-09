package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "surveillance_log")
public class SurveillanceLog extends AuditableBase<String> implements Serializable  {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "previous_status", nullable = false, length = 50)
    private String previousStatus;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

}
