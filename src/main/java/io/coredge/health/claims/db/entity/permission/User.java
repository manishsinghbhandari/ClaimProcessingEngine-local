package io.coredge.health.claims.db.entity.permission;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.enums.UserStatusEnum;
import jakarta.persistence.*;
import lombok.*;


@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends AuditableBase<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(name = "status", nullable = true)
    @Enumerated(EnumType.STRING)
    private UserStatusEnum status;

    @Column(name = "mobile_no",length = 15 )
    private String mobileNo;

    @Column(name = "email")
    private String email;

    @Column(name = "mnemonic")
    private String mnemonic;

    @Column(name = "addresses", columnDefinition = "TEXT")
    private String addresses;

    @PrePersist
    private void prePersist(){
        this.createdBy = HealthIdContextHolder.userId();
        this.lastModifiedBy = HealthIdContextHolder.userId();
        if(status == null){
            status =UserStatusEnum.ACTIVE;
        }
    }
}
