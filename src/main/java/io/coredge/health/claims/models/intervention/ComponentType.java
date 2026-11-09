package io.coredge.health.claims.models.intervention;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;
import lombok.Getter;

@Getter
public enum ComponentType {
  STRING(
      "String",
      List.of(
          ComponentCategory.EQUAL_IGNORE_CASE,
          ComponentCategory.EQUAL_STRING,
          ComponentCategory.CONTAIN_STRING,
          ComponentCategory.NOT_EQUAL_STRING)),
  LIST(
      "List",
      List.of(
          ComponentCategory.ANY_MATCH,
          ComponentCategory.ALL_MATCH,
          ComponentCategory.NON_MATCH,
          ComponentCategory.CONTAIN_LIST)),
  NUMBER(
      "Number",
      List.of(ComponentCategory.RANGE_NUMBER, ComponentCategory.EQUAL_NUMBER, ComponentCategory.NOT_EQUAL_NUMBER)),
  BOOLEAN("Boolean", List.of(ComponentCategory.EQUAL_BOOLEAN, ComponentCategory.NOT_EQUAL_BOOLEAN)),
  DATE(
      "Date",
      List.of(ComponentCategory.RANGE_DATE, ComponentCategory.EQUAL_DATE, ComponentCategory.NOT_EQUAL_DATE)),
  DATE_TIME(
      "Date Time",
      List.of(
          ComponentCategory.RANGE_DATE_TIME,
          ComponentCategory.NOT_EQUAL_DATE_TIME,
          ComponentCategory.NOT_EQUAL_DATE_TIME));

  @JsonCreator
  public static ComponentType fromValue(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return ComponentType.valueOf(value.toUpperCase());
  }

  private String value;
  private List<ComponentCategory> categories;

  ComponentType(String value, List<ComponentCategory> categories) {
    this.value = value;
    this.categories = categories;
  }
}
