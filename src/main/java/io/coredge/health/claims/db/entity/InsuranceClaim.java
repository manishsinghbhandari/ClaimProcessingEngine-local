package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.utils.FhirHelper;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.*;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.Formula;
import org.hl7.fhir.r4.model.Claim;

/** Model class that represents an insurance claim. */
@EqualsAndHashCode(callSuper = true)
@Inheritance(strategy = InheritanceType.JOINED)
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "claims")
@Slf4j
public class  InsuranceClaim extends ApproverBase implements Serializable {

  @Serial private static final long serialVersionUID = 1649522411236291450L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "claim_number", nullable = false, updatable = false)
  private Long claimNumber;

  @Column(name = "claim_id", unique = true, nullable = false, updatable = false)
  private UUID claimId;

  @Column(name = "pre_auth_id", unique = true, updatable = false)
  private UUID preAuthId;

  @ManyToOne
  @JoinColumn(name = "provider")
  private Provider provider;

  @ManyToOne
  @Cascade(org.hibernate.annotations.CascadeType.DETACH)
  @JoinColumn(name = "patient", nullable = false)
  private Member patient;

  /*
      @ManyToOne
      @JoinColumn(name = "location")
      private Location location;
  */

  @Basic
  @Column(name = "claim_code")
  private String claimCode;

  @Basic
  @Column(name = "date_from", nullable = false)
  private LocalDateTime dateFrom;

  @Basic
  @Column(name = "date_to", nullable = false)
  private LocalDateTime dateTo;

  @Basic
  @Column(name = "adjustment", columnDefinition = "TEXT")
  private String adjustment;

  @Basic
  @Column(name = "claimed_total")
  private BigDecimal claimedTotal;

  @Basic
  @Column(name = "approved_total")
  private BigDecimal approvedTotal;

  @Basic
  @Column(name = "date_processed")
  private Date dateProcessed;

  @Basic
  @Column(name = "explanation", columnDefinition = "TEXT")
  private String explanation;

  @Basic
  @Column(name = "rejection_reason", length = 255)
  private String rejectionReason;

  @Basic
  @Column(name = "guarantee_id", length = 255)
  private String guaranteeId;

  @Basic
  @Column(name = "external_id", length = 255)
  private String externalId;

  //    @ManyToOne
  //    @JoinColumn(name = "visit_type")
  //    private VisitType visitType;

  @Basic
  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private ExtensionStateCodeEnum status;

  @Basic
  @Column(name = "facility_provider_level", length = 255)
  private String facilityProviderLevel;

  @Basic
  @Column(name = "claim_type", length = 255)
  private String claimType;

  @Basic
  @Column(name = "access_code", length = 255)
  private String accessCode;

  @Basic
  @Column(name = "use", length = 255)
  private String use;

  @Column(name = "claim_response_id", unique = true, updatable = false)
  private String claimResponseId;

  @Column(name = "bundle_id", unique = true, updatable = false)
  private String bundleId;

  @Column(name = "is_phc_claim")
  private boolean isPHCClaim;

  @Column(name = "is_assigned")
  private boolean isAssigned;

  @Column(name = "civil_servant_inv_id")
  private String civilServantInvId;

  @Column(name = "source")
  private String source;

  @Column(name = "icd_codes")
  private String icdCodes;

  @Column(name = "is_ips_claim")
  private boolean isIpsClaim;

  @Column(name = "system_utilization_amount")
  private BigDecimal systemUtilizationAmount;

  @Column(name = "claim_payable_amount")
  private BigDecimal claimPayableAmount;

  @Basic
  @Column(name = "claim_payable_status")
  @Enumerated(EnumType.STRING)
  private ExtensionStateCodeEnum claimPayableStatus;

  @Basic
  @Column(name = "system_utilization_status")
  @Enumerated(EnumType.STRING)
  private ExtensionStateCodeEnum systemUtilizationStatus;

  @Column(name = "expiry_date")
  private LocalDate expiryDate;

  @Column(name = "audit_flag")
  private boolean auditFlag = false;

  @Column(name = "surveillance_status")
  private String surveillanceStatus;

  @Column(name = "claim_created_at")
  private LocalDate claimCreatedAt;

  @Column(name = "phc_claim_payments_id")
  private Long phcClaimPaymentsId;

  @Column(name = "policy_start_date")
  private LocalDateTime policyStartDate;

  @Column(name = "policy_end_date")
  private LocalDateTime policyEndDate;

  @Formula(
          "(select ci.code " +
                  "   from claim_items ci " +
                  "  where ci.claim_id = claim_id " +
                  "  order by ci.sequence asc " +
                  "  limit 1)"
  )
  private String shaCode;

  public String getShaCode() {
    return shaCode;
  }

  @PrePersist
  public void prePersist() {
    if (StringUtils.isNotEmpty(this.use)) {
      this.use = use.trim();
    }

    // Set the auditableData.
    if (StringUtils.isNotEmpty(HealthIdContextHolder.requestSource())) {
      this.source = HealthIdContextHolder.requestSource().toUpperCase();
    }
  }

  public void setClaimId(String claimId) {
    if (claimId.contains("Claim/")) {
      this.claimId = UUID.fromString(claimId.split("Claim/")[1]);
    } else {
      this.claimId = UUID.fromString(claimId);
    }
  }

  public static InsuranceClaim fromClaim(Claim claim, String claimId, String claimResponseId, String bundleId, Provider provider,
                                         Member member, ExtensionStateCodeEnum status){
    InsuranceClaim iClaim = new InsuranceClaim();
    iClaim.setClaimId(claimId);
    iClaim.setClaimResponseId(claimResponseId);
    iClaim.setBundleId(bundleId);
    iClaim.setExternalId(claimId);
    iClaim.setProvider(provider);
    iClaim.setPatient(member);
    iClaim.setStatus(status);
    if(StringUtils.isNotEmpty(provider.getLevel())){
      iClaim.setFacilityProviderLevel(provider.getLevel().toLowerCase());
    }
    //iClaim.setDateProcessed(DateType.today().getValue());
    iClaim.setClaimCode(claim.getType().getText());
    iClaim.setClaimedTotal(claim.getTotal().getValue());
    iClaim.setDateFrom(FhirHelper.convertToLocalDateTime(claim.getBillablePeriod().getStart()));
    iClaim.setDateTo(FhirHelper.convertToLocalDateTime(claim.getBillablePeriod().getEnd()));
    iClaim.setUse(claim.getUse().getDisplay().trim());
    if (claim.hasDiagnosis()) {
      List<String> icdCodeList = new ArrayList<>();
      for (var diagnosis : claim.getDiagnosis()) {
        if (diagnosis.hasDiagnosisCodeableConcept()) {
          for (var codable : diagnosis.getDiagnosisCodeableConcept().getCoding()) {
            if (codable.hasCode()) {
              icdCodeList.add(codable.getCode());
            }
          }
        }
      }
      // Join all codes with " | " and set to iClaim
      iClaim.setIcdCodes(String.join(" | ", icdCodeList));
    }
    if (claim.hasType()) {
      iClaim.setClaimType(claim.getType().getCoding().getFirst().getCode());
    }
    if (claim.hasSubType()) {
      iClaim.setAccessCode(claim.getSubType().getCoding().getFirst().getCode());
    }
    return iClaim;
  }

  public void addSystemUtilizationAmount(BigDecimal amount){
    if(this.systemUtilizationAmount == null){
      this.systemUtilizationAmount = BigDecimal.ZERO;
    }
    this.systemUtilizationAmount = this.systemUtilizationAmount.add(amount);
  }

  public void addClaimPayableAmount(BigDecimal amount){
    if(this.claimPayableAmount == null){
      this.claimPayableAmount = BigDecimal.ZERO;
    }
    this.claimPayableAmount = this.claimPayableAmount.add(amount);
  }

}
