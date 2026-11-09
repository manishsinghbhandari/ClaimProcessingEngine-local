package io.coredge.health.claims.models.master;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BenefitMasterResponse {

  private Long id;
  private String interventionCode;
  private String shaCategory;
  private String accessCode;
  private String subCategoryBenefitsPackage;
  private String interventionName;
  private boolean needsPreAuth;
  private String categoryHealthFacility;
  private String providerPaymentMechanism;
  private String gender;
  private String interventionQuantityYear;
  private String tariffs;
  private String limitIndividual;
  private String minAge;
  private String maxAge;
  private String limitsIndividualHousehold;
  private String phc;
  private String shif;
  private String eccif;
  private String pmsf;
  private String automateManual;
  private String code;
  private String appliesToSchemes;
  private String pmfCoverage;
}
