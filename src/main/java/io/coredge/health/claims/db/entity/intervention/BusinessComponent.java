package io.coredge.health.claims.db.entity.intervention;

import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.models.intervention.ComponentCategory;
import io.coredge.health.claims.models.intervention.ComponentType;
import io.coredge.health.claims.models.intervention.ResourceType;
import io.coredge.health.claims.models.intervention.Severity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Builder
@Data
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Cacheable
@Table(name = "business_component", schema = "public")
@DynamicInsert
public class BusinessComponent extends AuditableBase<String> {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  @Column(name = "code", nullable = false)
  @NotEmpty
  private String code;

  @Column(name = "name", nullable = false)
  @NotEmpty
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "path", nullable = false)
  @NotEmpty
  private String path;

  @Column(name = "severity", nullable = false)
  @NotNull
  @Enumerated(EnumType.STRING)
  private Severity severity;

  @Column(name = "resourceType", nullable = false)
  @NotNull
  @Enumerated(EnumType.STRING)
  private ResourceType resourceType;

  @Column(name = "ruleType", nullable = false)
  @NotNull
  @Enumerated(EnumType.STRING)
  private ComponentType componentType;

  @Column(name = "ruleCategory", nullable = false)
  @NotNull
  @Enumerated(EnumType.STRING)
  private ComponentCategory componentCategory;

  @Column(name = "value", nullable = false)
  @NotEmpty
  @JdbcTypeCode(SqlTypes.JSON)
  private String value;

  @Basic
  @Column(
      name = "effective_from",
      updatable = false,
      columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
  private LocalDateTime dateFrom;

  @Basic
  @Column(
      name = "effective_till",
      updatable = false,
      columnDefinition = "TIMESTAMP DEFAULT '9999-12-31T23:59:59.999'")
  private LocalDateTime dateTill;

  @Column(name = "isActive", columnDefinition = "BOOLEAN DEFAULT true")
  private Boolean isActive;

  @Column(name = "isRequired", columnDefinition = "BOOLEAN DEFAULT false")
  private Boolean isRequired;

  @Column(name = "isDeleted", columnDefinition = "BOOLEAN DEFAULT false")
  private Boolean isDeleted;

  @ColumnDefault("1")
  @Column(name = "version")
  private Integer version;
}
