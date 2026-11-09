package io.coredge.health.claims.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BenefitMastersDTO {
    private Long id;
    private String interventionCode;
    private String shaCategory;
    private String code;
    private String accessCode;
    private String subCategoryBenefitsPackage;
    private String interventionName;
    private boolean needsPreAuth;
    private String categoryHealthFacility;
    private String providerPaymentMechanism;
    private String gender;
    private String limitIndividualHousehold;
    private String interventionQuantityYear;
    private String tariffs;
    private String limitIndividual;
    private String minAge;
    private String maxAge;
    private String limitTYpe;
    private String phc;
    private String shif;
    private String eccif;
    private String automateManual;
    private List<String> diagnosisCategory;
    private List<String> diagnosisSubCategory;
    private Date dateFrom;
    private Date dateTill;
    private String status;
}
