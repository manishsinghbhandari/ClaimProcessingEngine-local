package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.db.repositories.converter.StringListConverter;
import io.coredge.health.claims.enums.Operator;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Cacheable
@EqualsAndHashCode(callSuper = true)
@Table(name = "intervention_duration", schema = "public")
public class InterventionDuration extends AuditableBase<String>  {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code" , nullable = false)
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "duration" , nullable = false)
    private Integer duration;

    @Basic
    @Column(name = "operator", nullable = false)
    @Convert(converter = Operator.OperatorConverter.class)
    private Operator operator;

    @Column(name = "effective_date", nullable = true)
    private LocalDateTime effectiveDate;

    @Convert(converter = StringListConverter.class)
    @Column(name = "allowed_combination", columnDefinition = "TEXT" , nullable = false)
    private List<String> allowedCombination = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "unallowed_combination", columnDefinition = "TEXT" , nullable = false)
    private List<String> unallowedCombination = new ArrayList<>();
}
