package io.coredge.health.claims.models.phccalculation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CountyStatDto(@JsonProperty("COUNTY") String county, @JsonProperty("REGISTRATIONS") Long registrations,
                            @JsonProperty("YEAR_MONTH") Long yearMonth,
                            @JsonProperty("LAST_UPDATED") String lastUpdated) {
}
