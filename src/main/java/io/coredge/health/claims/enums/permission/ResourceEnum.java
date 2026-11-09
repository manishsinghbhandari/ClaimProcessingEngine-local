package io.coredge.health.claims.enums.permission;

public enum ResourceEnum {
    ADMIN("Admin"),
    PREAUTHORIZATIONS("Preauthorization"),
    CLAIMS("Claim"),
    PHC_CLAIMS("Claim_Phc"),
    USER_MANAGE("User_Manage"),
    INTERVENTIONS("Intervention"),
    ASSIGNMENTS("Assignment"),
    CODES("Code"),
    ORGANIZATION("Organization"),
    PATIENT("Patient"),
    PRACTITIONER("Practitioner"),
    UNKNOWN("Unknown"),
    GLOBAL_PROPERTY("Global_Property"),
    CLAIM_BULK_APPROVE("Claim_Bulk_Approve"),
    REASON_MODULE("Reason_Code"),
            ;

    private final String displayName;

    ResourceEnum(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ResourceEnum fromDisplayName(String displayName) {
        for (ResourceEnum resource : ResourceEnum.values()) {
            if (resource.getDisplayName().equalsIgnoreCase(displayName)) {
                return resource;
            }
        }
        return UNKNOWN;
    }
}
