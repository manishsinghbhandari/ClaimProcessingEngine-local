package io.coredge.health.claims.enums;

import lombok.Getter;

@Getter
public enum IdentifierEnum {

    HOUSEHOLD_NUMBER("householdnumber"),
    SHA_NUMBER("shanumber"),
    NATIONAL_ID("nationalid"),
    PHONE_NUMBER("phonenumber");

    final String identifier;
    IdentifierEnum(String identifier) {
        this.identifier = identifier;

    }
}
