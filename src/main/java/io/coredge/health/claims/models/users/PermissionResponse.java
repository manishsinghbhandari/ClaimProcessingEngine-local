package io.coredge.health.claims.models.users;

import io.coredge.health.claims.enums.UserStatusEnum;
import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PermissionResponse {
    private String username;
    private UserStatusEnum status;
    private List<PermissionDetail> permissions;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PermissionDetail {
        private ResourceEnum resource;
        private List<ActionEnum> action;
    }
}
