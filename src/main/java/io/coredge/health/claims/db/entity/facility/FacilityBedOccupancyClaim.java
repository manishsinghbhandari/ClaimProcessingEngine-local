package io.coredge.health.claims.db.entity.facility;

import io.coredge.health.claims.db.entity.AuditableBase;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "facility_bed_occupancy_claim", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"facility_bed_occupancy_id", "claim_id"})
})
public class FacilityBedOccupancyClaim extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_bed_occupancy_id", nullable = false, foreignKey = @ForeignKey(name = "fk_occupancy_claim"))
    private FacilityBedOccupancy facilityBedOccupancy;

}
