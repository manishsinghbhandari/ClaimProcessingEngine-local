package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import java.util.List;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Cacheable
@Table(
    name = "m_category",
    indexes = {
      @Index(name = "idx_code", columnList = "code"),
      @Index(name = "idx_category_name", columnList = "category_name")
    })
public class CategoryMaster extends StatusBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "category_name", unique = true)
  private String categoryName;

}
