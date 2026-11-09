package io.coredge.health.claims.enums;

import lombok.Getter;

@Getter
public enum ProviderSearchType {
    ID("id"),
    MFL_CODE("mfl_code"),
    REG_NO("reg_no"),
    LEVEL("level"),
    FID_CODE("fid_code");

    private final String value;

    // Constructor to assign the value
    ProviderSearchType(String value) {
        this.value = value;
    }

}
