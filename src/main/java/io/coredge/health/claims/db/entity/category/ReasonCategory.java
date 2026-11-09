package io.coredge.health.claims.db.entity.category;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "reason_category",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "category_name"),
                @UniqueConstraint(columnNames = "prefix")
        }
)
@Getter
@Setter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class ReasonCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(nullable = false)
    private String prefix;

    @Column(name ="last_sequence", nullable = false)
    private String lastSequence;
}