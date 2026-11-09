package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Cacheable
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "m_sub_category",
    indexes = {@Index(name = "idx_sub_category_name", columnList = "sub_category_name")})
public class SubCategoryMaster extends StatusBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "sub_category_name", nullable = false)
  private String subCategoryName;

  @ManyToOne
  @JoinColumn(name = "category_master_id", nullable = false)
  private CategoryMaster categoryMaster;
}
