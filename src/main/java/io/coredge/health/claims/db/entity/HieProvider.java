package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.helpers.Helper;
import jakarta.persistence.Basic;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.ColumnDefault;
import org.hl7.fhir.r4.model.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.UUID;

@Slf4j
@Entity
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Cacheable
@Table(name = "hie_provider", schema = "public")
public class HieProvider {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  @Column(name="uuid", unique = true, nullable = true)
  private UUID uuid;

  @Basic
  @Column(name="identifier")
  private String identifier;

  @Basic
  @Column(name = "mfl_code", unique = true)
  private String mflCode;

  @Basic
  @Column(name = "name")
  private String name;

  @Basic
  @Column(name = "reg_no")
  private String regNo;

  @Basic
  @Column(name = "level")
  private String level;

  @Basic
  @Column(name = "license_status")
  private String licenseStatus;

  @Basic
  @Column(name = "license_no")
  private String licenseNumber;

  @Basic
  @Column(name = "registration_code")
  private String registrationCode;

  @Basic
  @Column(name = "is_active")
  @ColumnDefault("false")
  private Boolean isActive = false;

  @Basic
  @Column(name = "phone")
  private String phone;

  @Basic
  @Column(name = "email")
  private String email;

  @Basic
  @Column(name = "license_start")
  @Temporal(TemporalType.TIMESTAMP)
  private Date licenseEndDate;

  @Basic
  @Column(name = "license_end")
  @Temporal(TemporalType.TIMESTAMP)
  private Date licenseStartDate;

  @Basic
  @Column(name = "last_updated")
  @Temporal(TemporalType.TIMESTAMP)
  private Date lastUpdated;

  @Basic
  @Column(name = "facility_type")
  private String facilityType;

  @Basic
  @Column(name = "facility_agent")
  private String facilityAgent;

  @Basic
  @Column(name = "alias")
  private String alias;

  @Basic
  @Column(name = "address_line")
  private String addressLine;

  @Basic
  @Column(name = "physical_location")
  private String physicalLocation;

  @Basic
  @Column(name = "city")
  private String city;

  @Basic
  @Column(name = "district")
  private String district;

  @Basic
  @Column(name = "state")
  private String state;

  @Basic
  @Column(name = "county")
  private String county;

  @Basic
  @Column(name = "sub_county")
  private String subCounty;

  @Basic
  @Column(name = "town")
  private String town;

  @Basic
  @Column(name = "country")
  private String country;

  @Basic
  @Column(name = "part_of")
  private String entityType;

  @Basic
  @Column(name = "source")
  private String source;

  //Remove sanitized method
  public String getLevel(){
    // remove last code if ends with Alphabet like Level 3B to Level 3
    return Helper.removeLastAlphabet(this.level);
  }

  public String getLevelDetailed(){
    return this.level;
  }
  // Method to map FHIR Organization to OrganizationEntity
  public static HieProvider fromFhirOrganization(Organization fhirOrganization) {
    HieProvider entity = new HieProvider();

    // Set basic attributes
    entity.setMflCode(fhirOrganization.getIdPart());
    if(fhirOrganization.hasMeta() && fhirOrganization.getMeta().hasLastUpdated())
      entity.setLastUpdated(fhirOrganization.getMeta().getLastUpdated());

    entity.setSource("PROVIDER_PORTAL");
    entity.setIsActive(fhirOrganization.getActive());

    // Extract a provider type from extension
    fhirOrganization.getExtension().stream()
            .filter(ext -> ext.getUrl().endsWith("facility-level"))
            .findFirst()
            .ifPresent(ext -> {
              Coding providerTypeCoding = ((CodeableConcept) ext.getValue()).getCodingFirstRep();
                if(StringUtils.hasText(providerTypeCoding.getDisplay()))
                    entity.setLevel(providerTypeCoding.getDisplay().toLowerCase());
            });

    // Extract identifiers
    for (Identifier identifier : fhirOrganization.getIdentifier()) {
      String system = identifier.getSystem();
      String code = system.substring(system.lastIndexOf("/") + 1);
      switch (code) {
        case "mfl-code":
          entity.setMflCode(identifier.getValue());
          if (!entity.getMflCode().startsWith("MFL")) {
            entity.setMflCode("MFL-" + identifier.getValue());
          }
          break;
        case "fid":
          entity.setIdentifier(identifier.getValue());
          break;
        case "registration-number":
          entity.setRegNo(identifier.getValue());
          break;
        case "registration-code":
          entity.setRegistrationCode(identifier.getValue());
          break;
        case "provider-license":
          entity.setLicenseNumber(identifier.getValue());
          break;
      }
    }

    // Extract organization type
    if (!fhirOrganization.getType().isEmpty()) {
      Coding typeCoding = fhirOrganization.getTypeFirstRep().getCodingFirstRep();
      entity.setEntityType(typeCoding.getDisplay());
    }

    // Set name and alias
    entity.setName(fhirOrganization.getName());
    entity.setAlias(!fhirOrganization.getAlias().isEmpty() ? fhirOrganization.getAlias().get(0).getValue() : null);

    // Extract address fields
    if (!fhirOrganization.getAddress().isEmpty()) {
      Address address = fhirOrganization.getAddressFirstRep();
      entity.setAddressLine(!address.getLine().isEmpty() ? address.getLine().getFirst().getValue() : null);
      entity.setCity(address.getCity());
      entity.setDistrict(address.getDistrict());
      entity.setState(address.getState());
      entity.setCountry(address.getCountry());
    }

    // Extract partOf information
    if (fhirOrganization.hasPartOf()) {
      entity.setEntityType(fhirOrganization.getPartOf().getDisplay());
    }

    return entity;
  }

  // Method to map FHIR Organization to OrganizationEntity
  public static HieProvider fromHieFacility(Bundle bundle) {
    HieProvider entity = null;
    String structureDefUrl = "https://fr-kenyahie/StructureDefinition/";
    // Retrieve the first entry resource if it is an Organization
    if (!bundle.getEntry().isEmpty() && bundle.getEntryFirstRep().getResource() instanceof Organization organization) {
      entity = new HieProvider();
      // Extract basic details
      // entity.setUuid(UUID.fromString(organization.getIdElement().getIdPart()));
      entity.setName(organization.getName());
      entity.setIsActive(organization.getActive());
      entity.setSource("REGISTRY");
      entity.setLastUpdated(new Date());

      // Extract identifiers
      //System.out.println("Identifiers:");
      for (Identifier identifier : organization.getIdentifier()) {
        String system = identifier.getType().getCodingFirstRep().getSystem();
        String code = identifier.getType().getCodingFirstRep().getCode();
        String display = identifier.getType().getCodingFirstRep().getDisplay();
        String value = identifier.getValue();
        switch (code){
          case "registration-number":
            entity.setRegNo(value);
            break;
          case "mfl-code":
            entity.setMflCode(value);
            if (entity.getMflCode() != null && !entity.getMflCode().startsWith("MFL")) {
              entity.setMflCode("MFL-" + value);
            }
            break;
          case "license-number":
            entity.setLicenseNumber(value);
            if(identifier.hasPeriod() && identifier.getPeriod().hasStart())
              entity.setLicenseStartDate(identifier.getPeriod().getStart());
            if(identifier.hasPeriod() && identifier.getPeriod().hasEnd())
              entity.setLicenseEndDate(identifier.getPeriod().getEnd());
            break;
          case "fr-code":
            entity.setRegistrationCode(value);
            entity.setMflCode(entity.getMflCode() == null ? entity.getRegistrationCode() : entity.getMflCode());
            break;
          case "fid":
            entity.setIdentifier(value);
            break;
        }
      }

      // Extract contact details
      //System.out.println("Contact Details:");
      for (ContactPoint telecom : organization.getTelecom()) {
        switch (telecom.getSystem().toCode()){
          case "phone":
            entity.setPhone(telecom.getValue());
            break;
          case "email":
            entity.setEmail(telecom.getValue());
            break;
        }
        //System.out.printf("  %s: %s%n", telecom.getSystem().toCode(), telecom.getValue());
      }

      // Extract address and address extensions
      if (!organization.getAddress().isEmpty()) {
        Address address = organization.getAddressFirstRep();
        for (Extension extension : address.getExtension()) {
          String extnType = extension.getUrl().split(structureDefUrl)[1];
          switch (extnType){
            case "county":
              entity.setCounty(extension.getValueAsPrimitive().getValueAsString());
              break;
            case "sub-county":
              entity.setSubCounty(extension.getValueAsPrimitive().getValueAsString());
              break;
            case "postal-address":
              entity.setAddressLine(extension.getValueAsPrimitive().getValueAsString());
              break;
            case "physical-location":
              entity.setPhysicalLocation(extension.getValueAsPrimitive().getValueAsString());
              break;
            case "town":
              entity.setTown(extension.getValueAsPrimitive().getValueAsString());
              break;
          }
          //System.out.printf("  %s: %s%n", extension.getUrl(), extension.getValueAsPrimitive().getValueAsString());
        }
      }

      // Extract type (e.g., facility type)
      //System.out.println("Facility Type:");
      for(CodeableConcept type: organization.getType()){
        Coding code = type.getCoding().getFirst();
        switch (code.getCode()){
          case "specialized-tertiary-referral-hospital-":
            entity.setFacilityType(code.getDisplay());
            break;
        }
      }

      // Extract custom extensions (e.g., regulatory body, license status)
      //System.out.println("Custom Extensions:");
      for (Extension extension : organization.getExtension()) {
        String url = extension.getUrl();
        String extnType = extension.getUrl().split(structureDefUrl)[1];
        if(extension.getValue() instanceof  CodeableConcept) {
          CodeableConcept concept = (CodeableConcept) extension.getValue();
          switch (extnType) {
            case "license-status":
              entity.setLicenseStatus(concept.getCoding().getFirst().getCode());
              break;
            case "facility-agent":
              entity.setFacilityAgent(concept.getCoding().getFirst().getCode());
              break;
            case "regulatory-body":
              break;
            case "keph-level":
              entity.setLevel(concept.getCoding().getFirst().getCode().replace("-", " "));
              break;
          }
        }else{
          if(extnType.equalsIgnoreCase("license-status")){
            entity.setLicenseStatus(extension.getValueAsPrimitive().getValueAsString());
          }else{
           log.info("Extn has some issue: {}", url);
          }
        }
      }
    } else {
      log.info("No Organization resource found in the Bundle.");
    }
    return entity;
  }
}
