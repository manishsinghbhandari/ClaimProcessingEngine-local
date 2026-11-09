package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "admin_level_populations", schema = "public")
public class AdminLevelPopulation extends AuditableBase<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "admin_level_id", nullable = false)
    private AdminLevelOne adminLevelEntity;

    @Column(name = "population", nullable = false)
    private Long population;

    @Column(name = "month", nullable = false)
    private String month;

    public AdminLevelPopulation(AdminLevelOne adminLevelEntity, Long population, String month) {
        this.adminLevelEntity = adminLevelEntity;
        this.population = population;
        this.month = month;
    }
}

