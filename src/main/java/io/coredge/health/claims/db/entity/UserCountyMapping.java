package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing the mapping between users and counties for surveillance case follow-up.
 * Users with no mappings have access to all surveillance claims.
 * Users with mappings can only view claims from facilities in their assigned counties.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "user_county",
       uniqueConstraints = @UniqueConstraint(name = "uk_user_county",
                                             columnNames = {"username", "admin_level_one_id", "is_active"}))
public class UserCountyMapping extends AuditableBase<String> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "username", nullable = false)
    private String username;
    
    @Column(name = "admin_level_one_id", nullable = false)
    private Long adminLevelOneId;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_level_one_id", insertable = false, updatable = false)
    private AdminLevelOne adminLevelOne;
}
