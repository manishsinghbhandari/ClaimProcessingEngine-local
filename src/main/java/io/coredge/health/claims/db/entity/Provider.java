package io.coredge.health.claims.db.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.enums.IdentifierType;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.payments.utils.JpaCryptoConverter;
import jakarta.persistence.*;
import jakarta.persistence.Basic;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.ColumnDefault;
import org.hl7.fhir.r4.model.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
@EqualsAndHashCode(callSuper = true)
@Table(name = "provider", schema = "public")
public class Provider extends AuditableBase<String> {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "uuid", unique = true, nullable = true)
  private UUID uuid;

  @Basic
  @Column(name = "identifier", unique = true)
  private String identifier;

  @Basic
  @Column(name = "mfl_code", unique = true)
  private String mflCode;

  @Column(name = "fid_code")
  private String fidCode;

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
  @Column(name = "license_end")
  @Temporal(TemporalType.TIMESTAMP)
  private Date licenseEndDate;

  @Basic
  @Column(name = "license_start")
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
  @Column(name = "postal_address")
  private String postalAddress;

  @Basic
  @Column(name = "part_of")
  private String entityType;

  @Basic
  @Column(name = "source")
  private String source;

  @Basic
  @Column(name = "fid")
  private String fid;

  @Transient
  private Organization organization;

  @Transient
  private String countyCode;

  @Transient
  private String contactPerson;

  @Column(name = "efwa_check_enabled", nullable = false)
  private Boolean efwaCheckEnabled = false;

  @Column(name = "preauth_efwa_enabled", nullable = false)
  private Boolean preauthEfwaEnabled = false;
  // Facility Bank Details (Encrypted)
  @Column(name = "bank_name")
  @Convert(converter = JpaCryptoConverter.class)
  private String bankName;

  @Column(name = "account_number")
  @Convert(converter = JpaCryptoConverter.class)
  private String accountNumber;

  @Column(name = "account_name")
  @Convert(converter = JpaCryptoConverter.class)
  private String accountName;

  @Column(name = "bank_code")
  private String bankCode;

  @Column(name = "branch")
  private String branch;

  // Remove sanitized method
  public String getLevel() {
    // remove last code if ends with Alphabet like Level 3B to Level 3
    return Helper.removeLastAlphabet(this.level);
  }

  public String getLevelDetailed() {
    return this.level;
  }

  // Method to map FHIR Organization to OrganizationEntity
  public static Provider fromFhirOrganization(Organization fhirOrganization) {
    Provider entity = new Provider();

    // Set basic attributes
    if (fhirOrganization.getIdPart().startsWith("FID-")) {
      entity.setFidCode(fhirOrganization.getIdPart());
    } else {
      entity.setMflCode(fhirOrganization.getIdPart());
    }
    if (fhirOrganization.hasMeta() && fhirOrganization.getMeta().hasLastUpdated())
      entity.setLastUpdated(fhirOrganization.getMeta().getLastUpdated());

    entity.setSource("PROVIDER_PORTAL");
    entity.setIsActive(fhirOrganization.getActive());
    entity.setOrganization(fhirOrganization);

    // Extract a provider type from extension
    fhirOrganization.getExtension().stream()
        .filter(ext -> ext.getUrl().endsWith("facility-level"))
        .findFirst()
        .ifPresent(ext -> {
          Coding providerTypeCoding = ((org.hl7.fhir.r4.model.CodeableConcept) ext.getValue()).getCodingFirstRep();
          if (StringUtils.hasText(providerTypeCoding.getDisplay()))
            entity.setLevel(providerTypeCoding.getDisplay().toLowerCase());
        });

    // Extract identifiers
    for (Identifier identifier : fhirOrganization.getIdentifier()) {
      String system = identifier.getSystem();
      if (!StringUtils.hasText(system)) {
        continue;
      }
      String code = system.substring(system.lastIndexOf("/") + 1);
      switch (code) {
        case "mfl-code":
          entity.setMflCode(identifier.getValue());
          if (!entity.getMflCode().startsWith("MFL")) {
            entity.setMflCode("MFL-" + identifier.getValue());
          }
          // entity.setMfl(entity.getMflCode());
          break;
        case "fid":
          entity.setIdentifier(identifier.getValue());
          // entity.setFid(entity.getIdentifier());
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
    entity.setAlias(!fhirOrganization.getAlias().isEmpty() ? fhirOrganization.getAlias().getFirst().getValue() : null);

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
  public static Provider fromHieFacility(Bundle bundle, Long existingId) {
    Provider entity = null;
    String structureDefUrl = "https://fr-kenyahie/StructureDefinition/";
    // Retrieve the first entry resource if it is an Organization
    if (!bundle.getEntry().isEmpty() && bundle.getEntryFirstRep().getResource() instanceof Organization) {
      entity = new Provider();
      if (existingId != null) {
        entity.setId(existingId);
      }
      Organization organization = (Organization) bundle.getEntryFirstRep().getResource();

      // Extract basic details
      if (Helper.isValidUUID(organization.getIdElement().getIdPart())) {
        entity.setUuid(UUID.fromString(organization.getIdElement().getIdPart()));
      }
      entity.setName(organization.getName());
      entity.setIsActive(organization.getActive());
      entity.setSource("REGISTRY");
      entity.setLastUpdated(new Date());

      // Extract identifiers
      // System.out.println("Identifiers:");
      for (Identifier identifier : organization.getIdentifier()) {
        String system = identifier.getType().getCodingFirstRep().getSystem();
        String code = identifier.getType().getCodingFirstRep().getCode();
        String display = identifier.getType().getCodingFirstRep().getDisplay();
        String value = identifier.getValue();
        switch (code) {
          case "registration-number":
            entity.setRegNo(value);
            break;
          case "mfl-code":
            entity.setMflCode(value);
            // entity.setMfl(value);
            break;
          case "license-number":
            entity.setLicenseNumber(value);
            if (identifier.hasPeriod() && identifier.getPeriod().hasStart())
              entity.setLicenseStartDate(identifier.getPeriod().getStart());
            if (identifier.hasPeriod() && identifier.getPeriod().hasEnd())
              entity.setLicenseEndDate(identifier.getPeriod().getEnd());
            break;
          case "fr-code":
            entity.setFidCode(value);
            // entity.setFid(value);
            break;
          case "nnm":
            entity.setIdentifier(value);
            break;
        }
      }

      // Extract contact details
      // System.out.println("Contact Details:");
      for (ContactPoint telecom : organization.getTelecom()) {
        switch (telecom.getSystem().toCode()) {
          case "phone":
            entity.setPhone(telecom.getValue());
            break;
          case "email":
            entity.setEmail(telecom.getValue());
            break;
        }
        // System.out.printf(" %s: %s%n", telecom.getSystem().toCode(),
        // telecom.getValue());
      }

      // Extract address and address extensions
      if (!organization.getAddress().isEmpty()) {
        Address address = organization.getAddressFirstRep();
        for (Extension extension : address.getExtension()) {
          String extnType = extension.getUrl().split(structureDefUrl)[1];
          switch (extnType) {
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
          // System.out.printf(" %s: %s%n", extension.getUrl(),
          // extension.getValueAsPrimitive().getValueAsString());
        }
      }

      // Extract type (e.g., facility type)
      // System.out.println("Facility Type:");
      for (CodeableConcept type : organization.getType()) {
        Coding code = type.getCoding().getFirst();
        switch (code.getCode()) {
          case "specialized-tertiary-referral-hospital-":
            entity.setFacilityType(code.getDisplay());
            break;
        }
      }

      // Extract custom extensions (e.g., regulatory body, license status)
      // System.out.println("Custom Extensions:");
      for (Extension extension : organization.getExtension()) {
        String url = extension.getUrl();
        String extnType = extension.getUrl().split(structureDefUrl)[1];
        if (extension.getValue() instanceof CodeableConcept) {
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
        } else {
          if (extnType.equalsIgnoreCase("license-status")) {
            entity.setLicenseStatus(extension.getValueAsPrimitive().getValueAsString());
          } else {
            log.info("Extn has some issue: {}", url);
          }
        }
      }
    } else {
      log.info("No Organization resource found in the Bundle.");
    }
    return entity;
  }

  public static Provider fromJsonBundle(String json) {
    try {
      log.info("Processing Organization fromJsonBundle to Provider Object");
      if (json == null || json.isEmpty())
        return null;

      JsonNode entryNode = new ObjectMapper().readTree(json);
      Provider provider = new Provider();

      entryNode.path("identifier").forEach(identifier -> {
        String system = identifier.at("/type/coding/0/code").asText(null);
        String value = identifier.path("value").asText(null);
        if (system != null && value != null) {
          switch (IdentifierType.fromValue(system)) {
            case REGISTRATION_NUMBER -> provider.setRegNo(value);
            case LICENSE_NUMBER -> provider.setLicenseNumber(value);
            case FR_CODE -> provider.setFidCode(value);
            case FID -> provider.setFid(value);
            case MFL_CODE -> provider.setMflCode(value.startsWith("MFL") ? value : "MFL-" + value);
          }
        }
      });

      String providerName = entryNode.path("name").asText(null);
      provider.setName(providerName != null ? providerName.trim() : null);
      log.info("Provider Name: {}", provider.getName());

      entryNode.path("telecom").forEach(telecom -> {
        String system = telecom.path("system").asText(null);
        String value = telecom.path("value").asText(null);
        if ("phone".equals(system))
          provider.setPhone(value);
        if ("email".equals(system))
          provider.setEmail(value);
        if ("other".equals(system))
          provider.setContactPerson(value);
      });

      provider.setIsActive(entryNode.path("active").asBoolean(false));
      provider.setFacilityType(entryNode.at("/type/0/coding/0/code").asText(null));

      JsonNode addressNode = entryNode.path("address").get(0);
      if (addressNode != null) {
        StringBuilder addressLine = new StringBuilder();
        addressNode.path("extension").forEach(extension -> {
          String url = extension.path("url").asText(null);
          String urlExtensionCode = (url != null && url.contains("/")) ? url.substring(url.lastIndexOf('/') + 1) : null;

          String value = extension.path("valueString").asText(null);
          if (urlExtensionCode != null && value != null) {
            if (urlExtensionCode.equalsIgnoreCase("county"))
              provider.setCounty(value);
            if (urlExtensionCode.equalsIgnoreCase("county-code"))
              provider.setCountyCode(value);
            if (urlExtensionCode.equalsIgnoreCase("sub-county"))
              provider.setSubCounty(value);
            if (urlExtensionCode.equalsIgnoreCase("town"))
              provider.setTown(value);
            if (urlExtensionCode.equalsIgnoreCase("physical-location"))
              provider.setPhysicalLocation(value);
            if (urlExtensionCode.equalsIgnoreCase("postal-address"))
              provider.setPostalAddress(value);
            addressLine.append(value).append(", ");
          }
        });
        provider.setAddressLine(addressLine.toString().replaceAll(", $", ""));
      }

      entryNode.path("extension").forEach(extension -> {
        String url = extension.path("url").asText(null);
        if (url != null) {
          if (url.contains("license-status"))
            provider.setLicenseStatus(extension.path("valueString").asText(null));
          if (url.contains("keph-level"))
            provider.setLevel(extension.at("/valueCodeableConcept/coding/0/display").asText(null));
          if (url.contains("facility-agent"))
            provider.setFacilityAgent(extension.at("/valueCodeableConcept/coding/0/display").asText(null));
        }
      });

      entryNode.path("identifier").forEach(identifier -> {
        if ("license-number".equalsIgnoreCase(identifier.at("/type/coding/0/code").asText(null))) {
          provider.setLicenseNumber(identifier.path("value").asText(null));
          DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
          provider.setLicenseStartDate(parseDate(identifier.at("/period/start").asText(null), formatter));
          provider.setLicenseEndDate(parseDate(identifier.at("/period/end").asText(null), formatter));
        }
      });

      provider.setLastUpdated(Timestamp.valueOf(LocalDateTime.now()));
      log.info("fromJsonBundle to Provider Object : {}", provider.getId());
      return provider;
    } catch (Exception e) {
      log.error("Error parsing JSON: {}", e.getMessage());
      return null;
    }
  }

  private static Date parseDate(String dateStr, DateTimeFormatter formatter) {
    try {
      return (dateStr != null && !dateStr.isEmpty())
          ? Date.from(LocalDateTime.parse(dateStr, formatter).atZone(ZoneId.systemDefault()).toInstant())
          : null;
    } catch (Exception e) {
      log.error("Error parsing date: {}", e.getMessage());
      return null;
    }
  }
}
