package io.coredge.health.claims.payments.entity;

import io.coredge.health.claims.db.entity.AuditableBase;
import io.coredge.health.claims.payments.utils.JpaCryptoConverter;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Entity
@Table(name = "fund_bank_details")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FundBankDetails extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fund_scheme", nullable = false)
    private String fundScheme;

    @Column(name = "fund_bank_name", nullable = false)
    @Convert(converter = JpaCryptoConverter.class)
    private String fundBankName;

    @Column(name = "fund_branch_code", nullable = false)
    @Convert(converter = JpaCryptoConverter.class)
    private String fundBranchCode;

    @Column(name = "fund_bank_account_number", nullable = false)
    @Convert(converter = JpaCryptoConverter.class)
    private String fundBankAccountNumber;

    @Column(name = "erp_bank_name", nullable = false)
    private String erpBankName;

}
