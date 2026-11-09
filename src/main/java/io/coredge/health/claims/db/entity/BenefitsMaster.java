package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.db.entity.benefits.DocumentType;
import io.coredge.health.claims.db.entity.utilization.UtilizationPeriod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Cacheable
@DynamicUpdate
@Table(name = "benefits_master", schema = "public")
public class BenefitsMaster extends StatusBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intervention_code")
    private String interventionCode;

    @Column(name = "sha_Category")
    private String shaCategory;

    @Column(name = "code")
    private String code;

    @Column(name = "access_code")
    private String accessCode;

    @Column(name = "sub_category_benefits_package")
    private String subCategoryBenefitsPackage;

    @Column(name = "intervention_name")
    private String interventionName;

    @Column(name = "needs_pre_auth")
    private boolean needsPreAuth;

    @Column(name = "category_health_facility")
    private String categoryHealthFacility;

    @Column(name = "provider_payment_mechanism")
    private String providerPaymentMechanism;

    @Column(name = "gender")
    private String gender;

    @Column(name = "intervention_quantity_year")
    private String interventionQuantityYear;

    @Column(name = "tariffs")
    private String tariffs;

    @Column(name = "limit_individual")
    private String limitIndividual;

    @Column(name = "min_age")
    private String minAge;

    @Column(name = "max_age")
    private String maxAge;

    @Column(name = "limits_individual_household")
    private String limitsIndividualHousehold;

    @Column(name = "phc")
    private String phc;

    @Column(name = "shif")
    private String shif;

    @Column(name = "eccif")
    private String eccif;

    @Column(name = "pmsf")
    private String pmsf;

    @Column(name = "automate_manual")
    private String automateManual;

    @Basic
    @Column(name = "diagnosis_category")
    private List<String> diagnosisCategory;

    @Basic
    @Column(name = "diagnosis_sub_category")
    private List<String> diagnosisSubCategory;

    @Basic
    @Column(name = "effective_from", nullable = true)
    private Date dateFrom;

    @Basic
    @Column(name = "effective_till", nullable = true)
    private Date dateTill;

    @Column(name = "applies_to_schemes")
    private String appliesToSchemes;

    @Column(name = "pmf_coverage")
    private String pmfCoverage;

    @Column(name = "complexity")
    private String complexity;

    @Column(name = "group_name", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String groupName;

    @Column(name = "is_per_diem_club")
    private Boolean isPerDiemClub;

    @Column(name = "bed_type")
    private String bedType;

    @Column(name = "level_2_tariff")
    private Integer level2Tariff;

    @Column(name = "level_3_tariff")
    private Integer level3Tariff;

    @Column(name = "level_4_tariff")
    private Integer level4Tariff;

    @Column(name = "level_5_tariff")
    private Integer level5Tariff;

    @Column(name = "level_6_tariff")
    private Integer level6Tariff;

    @Column(name = "efwa_check_enabled", nullable = false)
    private Boolean efwaCheckEnabled = false;

    @Column(name = "preauth_efwa_enabled", nullable = false)
    private Boolean preauthEfwaEnabled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private AiRoute aiRoute;

    @Column(name = "daily_limit")
    private Integer dailyLimit;

    @Column(name = "sub_package")
    private String subPackage;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "benefit_document_type_mapping", joinColumns = @JoinColumn(name = "benefit_master_id"), inverseJoinColumns = @JoinColumn(name = "document_type_id"))
    private Set<DocumentType> documentTypes = new HashSet<>();

    @Column(name = "cycle_limit_in_day", nullable = false)
    private Integer cycleLimitInDay;

    @Basic
    @Column(name = "utilization_period" , nullable = false , columnDefinition = "VARCHAR(20) DEFAULT 'YEARLY'")
    @Enumerated(EnumType.STRING)
    private UtilizationPeriod utilizationPeriod;

    @Column(name = "period_count")
    private Integer periodCount;

    @Column(name = "combined_codes")
    private String combinedCodes;
}
