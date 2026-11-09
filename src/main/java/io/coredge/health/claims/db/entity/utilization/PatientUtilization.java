package io.coredge.health.claims.db.entity.utilization;


import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.Member;
import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "patient_utilization")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientUtilization {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Member patient;

    @Column(name = "individual_utilised_limit", nullable = false)
    private Integer individualUtilisedLimit;

    @Column(name = "household_utilised_limit", nullable = false)
    private Integer householdUtilisedLimit;

    @Column(name = "individual_max_during_period")
    private Integer individualMaxDuringPeriod;

    @Column(name = "individual_utilised_during_period")
    private Integer individualUtilisedDuringPeriod;

    @Column(name = "household_utilised_during_period")
    private Integer householdUtilisedDuringPeriod;

    @Basic
    @Enumerated(EnumType.STRING)
    @Column(name = "utilization_period")
    private UtilizationPeriod period;

    @Column(name = "last_usage_date")
    private LocalDate lastUsageDate;

    @Column(name ="utilized_per_diem")
    private Integer utilizedPerDiem;

    @Column(name = "valid_till", nullable = false)
    private LocalDate validTill;

    @OneToMany(mappedBy = "utilization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FundUtilizationLimit> fundUtilizationLimits = new ArrayList<>();

    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn(name = "benefits_master_id", nullable = false)
    private BenefitsMaster benefitsMaster;
}
