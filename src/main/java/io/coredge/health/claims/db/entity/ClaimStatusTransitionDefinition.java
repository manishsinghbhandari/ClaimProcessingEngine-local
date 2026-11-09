package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Entity representing the definition of allowed claim status transitions.
 *
 * <p>
 * The {@code ClaimStatusTransitionDefinition} class is used to configure and
 * enforce business rules related to claim status transitions within the system.
 * It defines which target status ({@code allowedStatus}) a claim is permitted
 * to move to from a given current status ({@code status}).
 * </p>
 *
 * <p>
 * This entity enables dynamic validation of claim workflow transitions by:
 * <ul>
 *     <li>Storing the current status of a claim.</li>
 *     <li>Defining the list of allowed next statuses for that current status.</li>
 *     <li>Restricting transitions only to those explicitly configured.</li>
 *     <li>Allowing activation/deactivation of specific transition rules using the {@code active} flag.</li>
 *     <li>Supporting contextual usage via the {@code use} field (e.g., module, workflow type, or environment).</li>
 * </ul>
 * </p>
 *
 * <p>
 * During claim processing, the system validates status updates against
 * this configuration to ensure that a claim cannot transition to an
 * unauthorized or invalid state. This helps maintain workflow integrity,
 * prevents inconsistent state changes, and enforces controlled lifecycle
 * management of claims.
 * </p>
 *
 * <p>
 * The class extends {@code AuditableBase<String>} to provide auditing
 * information such as created/modified metadata.
 * </p>
 *
 * <b>Example:</b><br>
 * If a claim is in {@code APPROVED} status, this entity can define that
 * it is only allowed to transition to {@code SENT_FOR_PAYMENT_PROCESSING} or {@code REJECTED},
 * but not directly to {@code CLINICAL_REVIEW}.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "claim_status_transition")
@Slf4j
public class ClaimStatusTransitionDefinition extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Basic
    @Column(name = "use", length = 255)
    private String use;

    @Basic
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ExtensionStateCodeEnum status;

    @Basic
    @Column(name = "allowed_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ExtensionStateCodeEnum allowedStatus;

    @Column(name = "active", nullable = false)
    private Boolean active;

}
