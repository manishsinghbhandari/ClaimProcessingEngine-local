package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.enums.ReviewPriorityEnum;
import io.coredge.health.claims.enums.Status;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Cacheable
@DynamicUpdate
@Table(name = "review_priority", schema = "public")
public class ReviewPriority extends AuditableBase<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Basic
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ReviewPriorityEnum category;

    @Column(name = "name" , nullable = false)
    private String name;

    @Column(name = "category_value" , nullable = false)
    private String categoryValue;

    @Column(name = "priority" , nullable = false)
    private Integer priority;

    @Basic
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

}
