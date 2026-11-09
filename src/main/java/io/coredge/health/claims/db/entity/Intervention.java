package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Cacheable
@Table(name = "interventions", schema = "public")
public class Intervention extends AuditableBase<String>{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intervention_code")
    private String interventionCode;

    @Column(name = "intervention_name")
    private String interventionName;

    @Column(name = "allowed_access_codes")
    private List<String> allowedAccessCodes;

    @Column(name = "allowed_gender")
    private List<String> allowedGenders;

    @Column(name = "categoryCode")
    private String categoryCode;

    @Column(name = "sha_Category")
    private String shaCategory;

    @Column(name = "sub_category_benefits_package")
    private String subCategoryBenefitsPackage;

    @Column(name = "needs_pre_auth")
    private boolean needsPreAuth;

    @Column(name = "allowed_facility_categories")
    private List<String> allowedFacilityCategories;

    @Column(name = "provider_payment_mechanism")
    private String providerPaymentMechanism;

    @Column(name = "diagnosis_category")
    private List<String> diagnosisCategory;

    @Column(name = "diagnosis_sub_category")
    private List<String> diagnosisSubCategory;

    @Column(name = "tariff", precision = 1)
    private Double tariff;

    @Column(name = "individual_limit_count")
    private Integer individualLimitCount;

    @Column(name = "household_limit_count")
    private Integer householdLimitCount;

    @Column(name = "individual_limit_amt", precision = 1)
    private Double individualLimitAmount;

    @Column(name = "household_limit_amt", precision = 1)
    private Double householdLimitAmount;

    @Column(name = "intervention_quantity_days")
    private String interventionQuantityDays;

    @Column(name = "intervention_quantity_year")
    private String interventionQuantityYear;

    @Column(name = "isDocAuthorizationReq")
    private Boolean isDocAuthorizationRequired;

    @Column(name = "isShaAuthorizationReq")
    private Boolean isShaAuthorizationRequired;

    @Column(name = "isSurveillanceReq")
    private Boolean isSurveillanceRequired;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "phc")
    private String phc;

    @Column(name = "shif")
    private String shif;

    @Column(name = "eccif")
    private String eccif;

    @Basic
    @Column(name = "effective_from", nullable = true)
    private Date dateFrom;

    @Basic
    @Column(name = "effective_till", nullable = true)
    private Date dateTill;

    @Column(name = "status")
    private String status;

    @Column(name = "state")
    private String state;

    @PrePersist
    public void prePersist(){
        if(this.allowedFacilityCategories == null || this.allowedFacilityCategories.isEmpty()){
            this.allowedFacilityCategories = new ArrayList<>();
            allowedFacilityCategories.add("level 1");
            allowedFacilityCategories.add("level 2");
            allowedFacilityCategories.add("level 3");
            allowedFacilityCategories.add("level 4");
            allowedFacilityCategories.add("level 5");
            allowedFacilityCategories.add("level 6");
        }
        if(this.allowedGenders == null || this.allowedGenders.isEmpty()){
            this.allowedGenders = new ArrayList<>();
            allowedGenders.add("male");
            allowedGenders.add("female");
        }

        if(this.allowedAccessCodes == null || this.allowedAccessCodes.isEmpty()){
            this.allowedAccessCodes = new ArrayList<>();
            allowedAccessCodes.add("inpatient");
            allowedAccessCodes.add("outpatient");
        }
    }
}
