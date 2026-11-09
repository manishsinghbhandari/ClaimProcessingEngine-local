package io.coredge.health.claims.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Getter
public enum BenefitMasterSearchType {
    CATEGORY("category"),
    CATEGORY_CODE("categoryCode"),
    SUB_CATEGORY("subCategory"),
    INTERVENTION("interventionCode");

    private final String value;

    // Constructor to assign the value
    BenefitMasterSearchType(String value) {
        this.value = value;
    }

    public enum CoverageType {
        SHA("SHA"),
        PMSF("PMSF"),
        SHA_PMSF("SHA,PMSF"),
        SHA_ECCIF("SHA,ECCIF"),
        ECCIF_PMSF("ECCIF,PMSF"),
        SHA_ECCIF_PMSF("SHA,ECCIF,PMSF");

        // List for ECCIF‑related coverage
        public static final List<String> TYPE_OF_COVERAGES_ECCIF = List.of(
                CoverageType.SHA_ECCIF.getValue(),
                CoverageType.ECCIF_PMSF.getValue(),
                CoverageType.SHA_ECCIF_PMSF.getValue()
        );

        // List for PMSF‑related coverage
        public static final List<String> TYPE_OF_COVERAGES_PMSF = List.of(
                CoverageType.SHA_PMSF.getValue(),
                CoverageType.ECCIF_PMSF.getValue(),
                CoverageType.SHA_ECCIF_PMSF.getValue()
        );

        private final String value;

        CoverageType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Optional<CoverageType> fromValue(String value) {
            return Arrays.stream(values())
                    .filter(type -> type.value.equalsIgnoreCase(value))
                    .findFirst();
        }
    }


}
