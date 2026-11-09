package io.coredge.health.claims.db.entity.BMS;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(
        name = "statuses",
        schema = "public",
        indexes = {
            @Index(name = "idx_sstatus_category_id", columnList = "status_category_id")
        }
)
public class Status {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "auto_number", nullable = false)
    private Integer autoNumber;

    @Column(name = "auto_code", nullable = false)
    private String autoCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_category_id")
    private StatusCategory statusCategory;

    @Column(name = "name", nullable = false)
    private String name;
}
