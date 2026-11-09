package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.enums.Status;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "System_vendor")
@Slf4j
public class SystemVendor extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "vendor_code", nullable = false)
    private String vendorCode;

    @Column(name = "vendor_name", nullable = false)
    private String vendorName;

    @Column(name = "bank_account_name", nullable = false)
    private String bankAccountName;

    @Column(name = "bank_account_number", nullable = false)
    private String bankAccountNumber;

    @Column(name = "bank_swift_code", nullable = false)
    private String bankSwiftCode;

    @Basic
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;
}
