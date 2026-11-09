package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

@Table(name = "package_master", schema = "public")
public class PackageMaster extends AuditableBase<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code" , nullable = false)
    private String code;

    @Column(name = "sha_category" , nullable = false)
    private String shaCategory;

    @Column(name = "status", unique = false, nullable = false)
    private String status;
}