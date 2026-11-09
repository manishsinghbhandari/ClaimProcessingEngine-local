package io.coredge.health.claims.models.users;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Exclude null fields from the JSO
public class ResourceResponse {
    private String resourceName;
    private List<String> actions;
}
