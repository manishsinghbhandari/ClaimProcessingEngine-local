package io.coredge.health.claims.models.intervention.dto;

import io.coredge.health.claims.models.intervention.ResourceType;
import io.coredge.health.claims.models.intervention.ComponentCategory;
import io.coredge.health.claims.models.intervention.ComponentType;
import io.coredge.health.claims.models.intervention.Severity;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BusinessComponentDTO {
  private Long id;
  private String ruleId;
  private String code;
  private String name;
  private String description;
  private String path;
  private Severity severity;
  private ResourceType resourceType;
  private ComponentType componentType;
  private ComponentCategory componentCategory;
  private String value;
  private LocalDateTime dateFrom;
  private LocalDateTime dateTill;
  private Boolean isActive;
  private Boolean isRequired;
}
