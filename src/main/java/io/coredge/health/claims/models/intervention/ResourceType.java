package io.coredge.health.claims.models.intervention;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import org.hl7.fhir.r5.model.Claim;

@Getter
public enum ResourceType {
  CLAIM("Claim", Claim.class),
  PRE_AUTHORIZATION("Pre-Authorization", Claim.class),
  ;

  @JsonCreator
  public static ResourceType fromValue(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return ResourceType.valueOf(value.toUpperCase());
  }

  private final String value;
  private final Class<?> clazz;

  ResourceType(String value, Class<?> clazz) {
    this.value = value;
    this.clazz = clazz;
  }
}
