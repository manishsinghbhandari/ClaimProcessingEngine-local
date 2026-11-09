package io.coredge.health.claims.db.entity.facility;

import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.db.entity.Provider;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "facility_bed_occupancy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class FacilityBedOccupancy extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "bed_type", nullable = false, length = 30)
    private String bedType; // e.g. Normal | HDU | ICU | Dialysis

    @Column(name = "occupancy_date", nullable = false)
    private LocalDate occupancyDate;

    @Column(name = "occupancy", nullable = false)
    private Integer occupancy;

    @Column(name = "facility_occupancy", nullable = false)
    private Integer facilityOccupancy;

    @Column(name = "flagged")
    private Boolean flagged = false;

    @OneToMany(mappedBy = "facilityBedOccupancy", cascade = CascadeType.ALL)
    private Set<FacilityBedOccupancyClaim> facilityBedOccupancyClaims = new HashSet<>();

}