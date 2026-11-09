package io.coredge.health.claims.enums;

public enum CategorySearchType {
  code("code");

  private final String value;

  // Constructor to assign the value
  CategorySearchType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
