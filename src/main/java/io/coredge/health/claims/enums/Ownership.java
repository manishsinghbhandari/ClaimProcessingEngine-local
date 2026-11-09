package io.coredge.health.claims.enums;

import lombok.Getter;

@Getter
public enum Ownership {
    GOK("Gok"),
    PRIVATE("Private"),
    FAITH_BASED("Faith-based"),
    Unknown("Unknown");

    private final String displayName;

    Ownership(String displayName) { this.displayName = displayName; }
}
