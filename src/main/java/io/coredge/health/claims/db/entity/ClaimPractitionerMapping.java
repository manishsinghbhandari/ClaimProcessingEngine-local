package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.constants.Constants;
import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@NoArgsConstructor
public class ClaimPractitionerMapping  {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "claim_practitioner_id")
    private Long mappingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false, referencedColumnName = "claim_number")
    private InsuranceClaim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practitioner_id", nullable = false, referencedColumnName = "id")
    private PractitionerDetails practitioner;

    @Column(name = "practitioner_type", nullable = false)
    private String practitionerType;

    public ClaimPractitionerMapping(InsuranceClaim claim, PractitionerDetails practitioner, String practitionerType) {
        this.claim = claim;
        this.practitioner = practitioner;
        this.practitionerType = practitionerType;
    }
}