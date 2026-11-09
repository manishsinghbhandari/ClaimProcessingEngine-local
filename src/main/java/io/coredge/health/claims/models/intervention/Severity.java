package io.coredge.health.claims.models.intervention;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum Severity {
  ERROR("Error"),
  ;

  @JsonCreator
  public static Severity fromValue(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return Severity.valueOf(value.toUpperCase());
  }

  private final String value;

  Severity(String value) {
    this.value = value;
  }
}
