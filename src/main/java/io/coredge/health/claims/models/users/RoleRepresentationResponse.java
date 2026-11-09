package io.coredge.health.claims.models.users;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleRepresentationResponse {
        private String id;
        private String name;
        private String description;
}


