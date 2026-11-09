package io.coredge.health.claims.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClaimDTO {

  @JsonProperty("category")
  private String category;

  @JsonProperty("serviceCategory")
  private String serviceCategory;

  @JsonProperty("subCategory")
  private String subCategory;

  @JsonProperty("interventionName")
  private String interventionName;

  @JsonProperty("interventionCode")
  private String interventionCode;

  @JsonProperty("diagnosisICDHeading")
  private String diagnosisICDHeading;

  @JsonProperty("blockICDcode")
  private String blockICDcode;

  @JsonProperty("preAuthDone")
  private boolean preAuthDone;

  @JsonProperty("facilityCatagory")
  private String facilityCategory;

  @JsonProperty("claimAmount")
  private double claimAmount;

  @JsonProperty("investigationTariff")
  private double investigationTariff;

  @JsonProperty("managementTariff")
  private double managementTariff;

  @JsonProperty("needsSurveillanceClaim")
  private boolean needsSurveillanceClaim;

  @JsonProperty("investigation_terrif")
  private double investigationTerrif;

  @JsonProperty("managementAmount")
  private double managementAmount;

  @JsonProperty("gender")
  private String gender;

  @JsonProperty("agge")
  private int age;

  @JsonProperty("timesInterventionUsed")
  private int timesInterventionUsed;
}
