package io.coredge.health.claims.db.entity;

import ca.uhn.fhir.util.StringUtil;
import io.coredge.health.claims.constants.Constants.ClaimLineItemStatus;
import io.coredge.health.claims.utils.FhirHelper;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.data.annotation.CreatedDate;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.coredge.health.claims.enums.FundType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Money;
/** Model class that represents an insurance Claim Line Item. */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "claim_items")
public class ClaimLineItem extends AuditableBase<String> implements Serializable {

  @Serial private static final long serialVersionUID = 1649522411236291450L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "item_id")
  private Long id;

  @ManyToOne
  @Cascade(org.hibernate.annotations.CascadeType.SAVE_UPDATE)
  @JoinColumn(name = "claim_id", nullable = false, referencedColumnName = "claim_id")
  private InsuranceClaim claim;

  @Basic
  @Column(name = "sequence")
  private Integer sequence;

  @Column(name = "code", length = 50)
  private String code;

  @Column(name = "serviced_date")
  private LocalDate servicedDate;

  @Column(name = "quantity")
  private Long quantity;

  @Column(name = "unit_price", precision = 10, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "net_value", precision = 10, scale = 2)
  private BigDecimal netValue;

  @Basic
  @Column(name = "approved_quantity")
  private Integer approvedQuantity;

  @Column(name = "requested_amount", precision = 12, scale = 2)
  private BigDecimal requestedAmount;

  @Basic
  @Column(name = "approved_price")
  private BigDecimal approvedPrice;

  @Basic
  @Column(name = "explanation", columnDefinition = "TEXT")
  private String explanation;

  @Basic
  @Column(name = "justification", columnDefinition = "TEXT")
  private String justification;

  @Basic
  @Column(name = "rejection_reason", length = 255)
  private String rejectionReason;

  @Basic
  @Column(name = "status")
  @Enumerated(EnumType.STRING)
  private ClaimLineItemStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "adjudication_params",columnDefinition = "jsonb")
  private String adjudicationParams;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "adjudication_rules",columnDefinition = "jsonb")
  private String adjudicationRule;

  @Basic
  @Column(name = "intervention_name", length = 255)
  private String interventionName;

  @Column(name = "pmf_item")
  private boolean pmfItem;

  @Column(name = "pmf_amount")
  private BigDecimal pmfAmount;

  @Column(name = "eccif_amount")
  private BigDecimal eccifAmount;

  @Column(name = "pre_auth_id", unique = true, updatable = false)
  private UUID preAuthId;

  @Column(name = "serviced_period_start")
  private LocalDate servicedPeriodStart;

  @Column(name = "serviced_period_end")
  private LocalDate servicedPeriodEnd;

  @Column(name = "shif_amount")
  private BigDecimal shifAmount;

  @CreatedDate
  @Column(
          name = "created_date",
          nullable = false,
          updatable = false,
          columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
  protected LocalDateTime creationDate;

  @OneToMany(mappedBy = "claimLineItem", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<ClaimItemDetail> details = new ArrayList<>();

  public static ClaimLineItem fromClaimItem(Claim.ItemComponent item, InsuranceClaim iClaim, String interventionName, String preAuthRefId){
    ClaimLineItem lineItem = new ClaimLineItem();
    lineItem.setCode(item.getProductOrService().getCoding().getFirst().getCode());
    lineItem.setClaim(iClaim);
    lineItem.setQuantity(item.getQuantity().getValue().longValue());
    lineItem.setSequence(item.getSequence());
    lineItem.setNetValue(item.getNet().getValue());
    lineItem.setUnitPrice(item.getUnitPrice().getValue());
    if (item.hasServicedPeriod() && item.getServicedPeriod().hasStart() && item.getServicedPeriod().hasEnd()) {
      lineItem.setServicedDate(FhirHelper.convertToLocalDate(item.getServicedPeriod().getStart()));
      lineItem.setServicedPeriodStart(FhirHelper.convertToLocalDate(item.getServicedPeriod().getStart()));
      lineItem.setServicedPeriodEnd(FhirHelper.convertToLocalDate(item.getServicedPeriod().getEnd()));
    }
    if (interventionName != null) {
      lineItem.setInterventionName(interventionName);
    }
    if (preAuthRefId != null) {
      lineItem.setPreAuthId(UUID.fromString(preAuthRefId));
    }
    if (item.hasExtension()) {
      for (Extension ext : item.getExtension()) {
        if (!ext.hasExtension()) {
          continue;
        }
        for (Extension sub : ext.getExtension()) {
          String url = sub.getUrl();
          if (url == null || !sub.hasValue() || !(sub.getValue() instanceof Money)) {
            continue;
          }
          BigDecimal amount = ((Money) sub.getValue()).getValue();
          if (amount == null) {
            continue;
          }
          if (FundType.PMSF.toString().equalsIgnoreCase(url) && amount.compareTo(BigDecimal.ZERO) > 0) {
            lineItem.setPmfAmount(amount);
            lineItem.setPmfItem(true);
          } else if (FundType.ECCIF.toString().equalsIgnoreCase(url) && amount.compareTo(BigDecimal.ZERO) > 0) {
            lineItem.setEccifAmount(amount);
          }
        }
      }
    }

    // Parse item-level details (e.g. SHA-06 regimens, treatment sub-interventions)
    if (item.hasDetail()) {
        List<ClaimItemDetail> details = new ArrayList<>();
            for (Claim.DetailComponent detail : item.getDetail()) {
                ClaimItemDetail detailEntity = ClaimItemDetail.fromFhirDetail(detail, lineItem);
                      details.add(detailEntity);
                }
        lineItem.setDetails(details);
    }

    return lineItem;
  }
}

