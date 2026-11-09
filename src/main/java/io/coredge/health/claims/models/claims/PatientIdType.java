package io.coredge.health.claims.models.claims;

import lombok.Getter;

@Getter
public enum PatientIdType {
    nationalId("National Id"),
    shaNumber("SHA Number"),
    crId("CR Number");

    private final String value;

    PatientIdType(String value) {
        this.value = value;
    }
}
