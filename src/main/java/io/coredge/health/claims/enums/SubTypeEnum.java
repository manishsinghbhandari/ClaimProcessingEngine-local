package io.coredge.health.claims.enums;

import lombok.Getter;

@Getter
public enum SubTypeEnum {
    EMERGENCY("outpatient"),
    INPATIENT("inpatient"),
    OUTPATIENT("emergency");

    public final String displayName;

    SubTypeEnum(String displayName){ this.displayName=displayName; }

}
