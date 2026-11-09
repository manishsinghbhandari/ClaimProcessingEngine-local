package io.coredge.health.claims.db.entity.benefits;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name = "document_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class DocumentType extends AuditableBase<String> {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Include
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    //@JsonIgnore
    @ManyToMany(mappedBy = "documentTypes")
    private Set<BenefitsMaster> benefitsMasters = new HashSet<>();
}

