package io.coredge.health.claims.models.intervention.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.coredge.health.claims.models.intervention.ResourceType;
import io.coredge.health.claims.models.intervention.ComponentCategory;
import io.coredge.health.claims.models.intervention.ComponentType;
import io.coredge.health.claims.models.intervention.Severity;
import java.time.LocalDateTime;
import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessComponentResponse {

  @JsonProperty("id")
  private String id;

  @JsonProperty("code")
  private String code;

  @JsonProperty("name")
  private String name;

  @JsonProperty("description")
  private String description;

  @JsonProperty("path")
  private String path;

  @JsonProperty("severity")
  private Severity severity;

  @JsonProperty("resourceType")
  private ResourceType resourceType;

  @JsonProperty("ruleType")
  private ComponentType componentType;

  @JsonProperty("ruleCategory")
  private ComponentCategory componentCategory;

  @JsonProperty("value")
  private String value;

  @JsonProperty("dateFrom")
  private LocalDateTime dateFrom;

  @JsonProperty("dateTill")
  private LocalDateTime dateTill;

  @JsonProperty("isActive")
  private Boolean isActive;

  @JsonProperty("isRequired")
  private Boolean isRequired;
}
