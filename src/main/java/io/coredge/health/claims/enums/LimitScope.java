package io.coredge.health.claims.enums;

public enum LimitScope {
    HOUSEHOLD,
    NOT_APPLICABLE,
    NONE,
    INDIVIDUAL;

    public static LimitScope fromString(String scope) {
        if (scope == null) {
            return null;
        }
        for (LimitScope limitScope : LimitScope.values()) {
            if (limitScope.name().equalsIgnoreCase(scope)) {
                return limitScope;
            }
        }
        return null;
    }
}
