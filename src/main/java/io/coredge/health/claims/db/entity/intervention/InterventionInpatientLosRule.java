package io.coredge.health.claims.db.entity.intervention;

import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.db.entity.PackageMaster;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.Cascade;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Builder
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "intervention_inpatient_los_rules", schema = "public")
public class InterventionInpatientLosRule extends AuditableBase<String>  {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @OneToOne
    @Cascade(org.hibernate.annotations.CascadeType.SAVE_UPDATE)
    @JoinColumn(name = "package_master_id", nullable = false)
    @NotBlank
    private PackageMaster packageMasterId;

    @Column(name = "level", nullable = false)
    private String level;

    @Column(name = "ownership")
    private String ownership;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "operator")
    private String operator;

    @Column(name = "action_status")
    private String actionStatus;
}
