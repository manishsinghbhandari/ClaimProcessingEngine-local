package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.*;
import org.hl7.fhir.r4.model.Claim;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Entity representing a claim item sub-detail record.
 * Maps to the FHIR Claim.item[].detail[].subDetail[] structure.
 * Stored in the claim_item_sub_details table.
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "claim_item_sub_details")
public class ClaimItemSubDetail extends AuditableBase<String> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detail_id", nullable = false)
    private ClaimItemDetail claimItemDetail;

    @Column(name = "sequence")
    private Integer sequence;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "code_display", length = 255)
    private String codeDisplay;

    @Column(name = "code_system", length = 255)
    private String codeSystem;

    @Column(name = "quantity")
    private Long quantity;

    @Column(name = "unit_price", precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "factor", precision = 10, scale = 4)
    private BigDecimal factor;

    @Column(name = "net_value", precision = 19, scale = 2)
    private BigDecimal netValue;

    @Column(name = "currency", length = 10)
    private String currency;

    /**
     * Creates a ClaimItemSubDetail from a FHIR SubDetailComponent.
     *
     * @param subDetail    the FHIR sub-detail component
     * @param parentDetail the parent ClaimItemDetail entity
     * @return populated ClaimItemSubDetail
     */
    public static ClaimItemSubDetail fromFhirSubDetail(Claim.SubDetailComponent subDetail, ClaimItemDetail parentDetail) {
        ClaimItemSubDetail entity = new ClaimItemSubDetail();
        entity.setClaimItemDetail(parentDetail);
        entity.setSequence(subDetail.getSequence());
        // Parse productOrService coding
        if (subDetail.hasProductOrService() && subDetail.getProductOrService().hasCoding()) {
            var coding = subDetail.getProductOrService().getCoding().getFirst();
            entity.setCode(coding.getCode());
            entity.setCodeDisplay(coding.getDisplay());
            entity.setCodeSystem(coding.getSystem());
        }
        // Parse quantity
        if (subDetail.hasQuantity() && subDetail.getQuantity().hasValue()) {
            entity.setQuantity(subDetail.getQuantity().getValue().longValue());
        }
        // Parse unitPrice
        if (subDetail.hasUnitPrice() && subDetail.getUnitPrice().hasValue()) {
            entity.setUnitPrice(subDetail.getUnitPrice().getValue());
            if (subDetail.getUnitPrice().hasCurrency()) {
                entity.setCurrency(subDetail.getUnitPrice().getCurrency());
            }
        }
        // Parse factor
        if (subDetail.hasFactor()) {
            entity.setFactor(subDetail.getFactor());
        }
        // Parse net
        if (subDetail.hasNet() && subDetail.getNet().hasValue()) {
            entity.setNetValue(subDetail.getNet().getValue());
            if (subDetail.getNet().hasCurrency()) {
                entity.setCurrency(subDetail.getNet().getCurrency());
            }
        }
        return entity;
    }
}