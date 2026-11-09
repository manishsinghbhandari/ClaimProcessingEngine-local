package io.coredge.health.claims.service.intervention;

import io.coredge.health.claims.models.intervention.*;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class PageDropDown {

  private final Map<String, String> severity;
  private final Map<String, String> ruleTypes;
  private final Map<String, Map<String, String>> ruleCategory;
  private final Map<String, String> resourceType;

  public PageDropDown() {
    severity =
        Arrays.stream(Severity.values())
            .collect(Collectors.toMap(Severity::getValue, Severity::toString));
    ruleTypes =
        Arrays.stream(ComponentType.values())
            .collect(Collectors.toMap(ComponentType::getValue, ComponentType::toString));
    ruleCategory =
        Arrays.stream(ComponentType.values())
            .collect(
                Collectors.toMap(
                    ComponentType::getValue,
                    rule ->
                        rule.getCategories().stream()
                            .collect(
                                Collectors.toMap(
                                    ComponentCategory::getCategory, ComponentCategory::toString))));
    resourceType =
        Arrays.stream(ResourceType.values())
            .collect(Collectors.toMap(ResourceType::getValue, ResourceType::toString));
  }
}
