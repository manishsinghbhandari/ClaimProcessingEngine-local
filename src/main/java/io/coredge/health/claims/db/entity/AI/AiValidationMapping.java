package io.coredge.health.claims.db.entity.AI;

import io.coredge.health.claims.db.entity.BenefitsMaster;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ai_validation_mapping")
@Setter
@Getter
public class AiValidationMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "benefit_master_id", nullable = false)
    private BenefitsMaster benefitMaster;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_validation_id", nullable = false)
    private AIValidation aiValidation;

    @Column
    private String use;

}
