package io.coredge.health.claims.models.users;

import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PermissionRequest {

    private String username;
    private List<PermissionDetail> permissions;

    // Nested class to represent individual permission details.
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PermissionDetail {
        private ResourceEnum resource;
        private List<ActionEnum> action;
    }
}
