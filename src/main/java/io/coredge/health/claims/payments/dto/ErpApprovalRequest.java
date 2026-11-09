package io.coredge.health.claims.payments.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpApprovalRequest {
    private String claimId;
    private String fid;
    private String fundScheme;
    private String beneficiaryShaId;
    private BigDecimal claimAmount;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime ceoApprovalDate;
    private String status;
    private String companyBankAccount;
    private String companyBankName;
    private String erp;
}
