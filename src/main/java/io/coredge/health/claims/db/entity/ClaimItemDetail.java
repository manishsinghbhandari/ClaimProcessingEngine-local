package io.coredge.health.claims.db.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hl7.fhir.r4.model.Claim;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "claim_item_details")
public class ClaimItemDetail extends AuditableBase<String> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private ClaimLineItem claimLineItem;

    @Column(name = "sequence")
    private Integer sequence;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "code_display", length = 255)
    private String codeDisplay;

    @Column(name = "code_system", length = 255)
    private String codeSystem;

    @Column(name = "category", length = 100)
    private String category;

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

    @OneToMany(mappedBy = "claimItemDetail", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ClaimItemSubDetail> subDetails = new ArrayList<>();
    /**
     * Creates a ClaimItemDetail from a FHIR DetailComponent.
     *
     * @param detail     the FHIR detail component
     * @param parentItem the parent ClaimLineItem entity
     * @return populated ClaimItemDetail
     */
    public static ClaimItemDetail fromFhirDetail(Claim.DetailComponent detail, ClaimLineItem parentItem) {
        ClaimItemDetail entity = new ClaimItemDetail();
        entity.setClaimLineItem(parentItem);
        entity.setSequence(detail.getSequence());
        // Parse productOrService coding
        if (detail.hasProductOrService() && detail.getProductOrService().hasCoding()) {
            var coding = detail.getProductOrService().getCoding().getFirst();
            entity.setCode(coding.getCode());
            entity.setCodeDisplay(coding.getDisplay());
            entity.setCodeSystem(coding.getSystem());
        }
        // Parse category
        if (detail.hasCategory() && detail.getCategory().hasCoding()) {
            entity.setCategory(detail.getCategory().getCoding().getFirst().getCode());
        }
        // Parse quantity
        if (detail.hasQuantity() && detail.getQuantity().hasValue()) {
            entity.setQuantity(detail.getQuantity().getValue().longValue());
        }
        // Parse unitPrice
        if (detail.hasUnitPrice() && detail.getUnitPrice().hasValue()) {
            entity.setUnitPrice(detail.getUnitPrice().getValue());
            if (detail.getUnitPrice().hasCurrency()) {
                entity.setCurrency(detail.getUnitPrice().getCurrency());
            }
        }
        // Parse factor
        if (detail.hasFactor()) {
            entity.setFactor(detail.getFactor());
        }
        // Parse net
        if (detail.hasNet() && detail.getNet().hasValue()) {
            entity.setNetValue(detail.getNet().getValue());
            if (detail.getNet().hasCurrency()) {
                entity.setCurrency(detail.getNet().getCurrency());
            }
        }
        // Parse subDetails
        if (detail.hasSubDetail()) {
            List<ClaimItemSubDetail> subDetails = new ArrayList<>();
            for (Claim.SubDetailComponent subDetail : detail.getSubDetail()) {
                ClaimItemSubDetail subEntity = ClaimItemSubDetail.fromFhirSubDetail(subDetail, entity);
                subDetails.add(subEntity);
            }
            entity.setSubDetails(subDetails);
        }
        return entity;
    }

}
