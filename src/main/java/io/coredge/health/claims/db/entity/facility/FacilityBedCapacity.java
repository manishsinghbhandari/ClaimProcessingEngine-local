package io.coredge.health.claims.db.entity.facility;

import io.coredge.health.claims.db.entity.AuditableBase;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "facility_bed_capacity", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class FacilityBedCapacity extends AuditableBase<String> {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "provider_id", nullable = false)
        private Long providerId;

        @Column(name = "total_beds", nullable = false)
        private int totalBeds;

        @Column(name = "normal_beds", nullable = false)
        private int normalBeds;

        @Column(name = "icu_beds", nullable = false)
        private int icuBeds;

        @Column(name = "hdu_beds", nullable = false)
        private int hduBeds;

        @Column(name = "dialysis_beds", nullable = false)
        private int dialysisBeds;

        @Column(name = "number_of_cots", nullable = false)
        private int numberOfCots;
    }
