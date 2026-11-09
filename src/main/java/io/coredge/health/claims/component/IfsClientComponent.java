package io.coredge.health.claims.component;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import io.coredge.health.claims.models.eclaims.IfsRequest;
import io.coredge.health.claims.models.eclaims.Invoice;
import io.coredge.health.claims.service.ClaimAttributeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;


@Component
@Slf4j
public class IfsClientComponent implements Function<CreateClaimFundScheme, IfsRequest> {

    private final ClaimAttributeService claimAttributeService;

    public IfsClientComponent(ClaimAttributeService claimAttributeService) {
        this.claimAttributeService = claimAttributeService;
    }

    @Override
    public IfsRequest apply(CreateClaimFundScheme fundScheme) {
        // Option 1: If you want comma-separated fundSchemeType
        String fundSchemeTypes = fundScheme.getFundScheme()
                .stream().filter(data -> data.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(CreateClaimFundScheme.FundScheme::getFundSchemeType)
                .findFirst()
                .orElse(null);
        Invoice.InvoiceBuilder invoiceBuilder = Invoice.builder()
                .claimId(addClaimNumber(fundScheme.getClaimNo(), fundSchemeTypes))
                .vendorCode(fundScheme.getVendorCode())
                .vendorName(fundScheme.getVendorName())
                .provideInvoiceNo(fundScheme.getInvoiceNumber())
                .providerInvoiceDate(fundScheme.getProviderInvoiceDate())
                .serviceDate(fundScheme.getServiceEndDate())
                .claimAmount(fundScheme.getClaimAmount())
                .beneficiaryShaNo(fundScheme.getBeneficiaryShaNo())
                .beneficiaryName(fundScheme.getBeneficiaryName())
                .fundSchemeType(fundSchemeTypes)
                .serviceCode(fundScheme.getInterventionCodes())
                .claimRefLink(fundScheme.getClaimReferenceLink())
                .provideInvoiceNo(fundScheme.getInvoiceNumber())
                .coverageType(getCoverageType(fundScheme.getClaimNo()))
                .providerInvoiceDate(fundScheme.getProviderInvoiceDate())
                .verifiedBy(fundScheme.getVerifiedBy());

// ✅ Null check here
        if (fundScheme.getFundDistribution() != null) {
            invoiceBuilder.fundDistribution(
                    Invoice.FundDistribution.builder()
                            .providerShare(
                                    fundScheme.getFundDistribution().getProviderShare() != null
                                            ? Invoice.Amount.builder()
                                            .amount(fundScheme.getFundDistribution().getProviderShare().getAmount())
                                            .build()
                                            : null
                            )
                            .systemUtilization(
                                    fundScheme.getFundDistribution().getSystemUtilization() != null
                                            ? Invoice.Amount.builder()
                                            .amount(fundScheme.getFundDistribution().getSystemUtilization().getAmount())
                                            .build()
                                            : null
                            )
                            .build()
            );
        }

        Invoice invoice = invoiceBuilder.build();
        return IfsRequest.builder().invoices(invoice).build();
    }

    // Added the Coverage Type.
    private String getCoverageType(String claimId) {
        String sanitizedClaimId = claimId.contains("_") ? claimId.split("_")[0] : claimId;
        return claimAttributeService.getActiveAttribute(UUID.fromString(sanitizedClaimId)) != null ?
                claimAttributeService.getActiveAttribute(UUID.fromString(sanitizedClaimId)).getCoverageType() : "";
    }

    private String addClaimNumber(String claimNumber, String fundSchemeType) {
        log.info("Claim ID without expected suffix: {}", claimNumber);
        List<String> CLAIM_SUFFIXES = List.of(
                Constants.ClaimFundType.PMSF,
                Constants.ClaimFundType.SHIF,
                Constants.ClaimFundType.ECCIF,
                Constants.ClaimFundType.PHC
        );
        boolean hasSuffix = CLAIM_SUFFIXES.stream()
                .anyMatch(suffix -> claimNumber.toLowerCase().endsWith(suffix.toLowerCase()));

        String childClaimNo = hasSuffix
                ? claimNumber
                : claimNumber + "_" + fundSchemeType;

        if (!hasSuffix) {
            log.info("Updated Claim ID with suffix: {}", childClaimNo);
        }
        return childClaimNo;
    }


}

