package io.coredge.health.claims.models.intervention;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.function.BiPredicate;
import lombok.Getter;

@Getter
public enum RuleOperator {
  AND("and", (a, b) -> a && b),
  OR("or", (a, b) -> a || b),
  NOT("Not", (a, b) -> !a),
  THEN("then", (a, b) -> a),
  ;

  @JsonCreator
  public static RuleOperator fromValue(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return RuleOperator.valueOf(value.toUpperCase());
  }

  private final String operation;
  private final BiPredicate<Boolean, Boolean> predicate;

  RuleOperator(String operation, BiPredicate<Boolean, Boolean> predicate) {
    this.operation = operation;
    this.predicate = predicate;
  }
}
