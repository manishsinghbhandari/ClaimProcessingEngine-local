package io.coredge.health.claims.models.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SuggestionResponse {
    private String code;
    private String name;
    private String details;

}
