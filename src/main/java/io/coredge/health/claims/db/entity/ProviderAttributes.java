package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.enums.AttributeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "provider_attributes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderAttributes extends AuditableBase<String>{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "is_vems_supported", nullable = false)
    private boolean isVemsSupported = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttributeStatus status;
}
