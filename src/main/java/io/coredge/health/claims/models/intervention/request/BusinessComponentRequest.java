package io.coredge.health.claims.models.intervention.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.coredge.health.claims.models.intervention.ResourceType;
import io.coredge.health.claims.models.intervention.ComponentCategory;
import io.coredge.health.claims.models.intervention.ComponentType;
import io.coredge.health.claims.models.intervention.Severity;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BusinessComponentRequest {

  @JsonProperty("code")
  @NotEmpty
  private String code;

  @JsonProperty("name")
  @NotEmpty
  private String name;

  @JsonProperty("description")
  private String description;

  @JsonProperty("path")
  @NotEmpty
  private String path;

  @JsonProperty("severity")
  @NotNull
  private Severity severity;

  @JsonProperty("resourceType")
  @NotNull
  private ResourceType resourceType;

  @JsonProperty("ruleType")
  @NotNull
  private ComponentType componentType;

  @JsonProperty("ruleCategory")
  @NotNull
  private ComponentCategory componentCategory;

  @JsonProperty("value")
  @NotEmpty
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
