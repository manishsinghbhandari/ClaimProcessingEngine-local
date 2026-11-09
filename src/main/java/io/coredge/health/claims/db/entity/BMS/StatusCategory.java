package io.coredge.health.claims.db.entity.BMS;

import jakarta.persistence.*;

@Entity
@Table(
        name = "status_categories",
        schema = "public",
        indexes = {
            @Index(name = "idx_status_category_code", columnList = "code")
        }
)
public class StatusCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;
}
