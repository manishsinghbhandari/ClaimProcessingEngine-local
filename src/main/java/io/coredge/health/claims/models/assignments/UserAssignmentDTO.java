package io.coredge.health.claims.models.assignments;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserAssignmentDTO {
    private String resourceType;
    private String AssignmentType;
    private List<UserWithPermissions> users;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UserWithPermissions {
        private Long userId;
        private String username;
    }
}

