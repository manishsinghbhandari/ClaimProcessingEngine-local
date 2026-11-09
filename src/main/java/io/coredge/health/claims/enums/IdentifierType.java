package io.coredge.health.claims.enums;
import lombok.Getter;

@Getter
public enum IdentifierType {

    FR_CODE("fr-code"),
    MFL_CODE("mfl-code"),
    FID("fid"),
    REGISTRATION_NUMBER("registration-number"),
    LICENSE_NUMBER("license-number");

    private final String identifierType;

    IdentifierType(String identifierType){
        this.identifierType = identifierType;
    }

    public static IdentifierType getIdentifier(String identifierCode){
        if(identifierCode.contains("FID-"))
            return IdentifierType.FR_CODE;
        else if(identifierCode.contains("MFL-"))
            return IdentifierType.MFL_CODE;
        return IdentifierType.REGISTRATION_NUMBER;
    }


    private IdentifierType getIdentifierType(String identifierCode) {
        if (identifierCode == null) {
            throw new IllegalArgumentException("Identifier code cannot be null");
        }

        return switch (identifierCode.toLowerCase()) {
            case "fr-code" -> IdentifierType.FR_CODE;
            case "mfl-code" -> IdentifierType.MFL_CODE;
            case "registration-number" -> IdentifierType.REGISTRATION_NUMBER;
            case "license-number" -> IdentifierType.LICENSE_NUMBER;
            case "fid" -> IdentifierType.FID;
            default -> null;
        };
    }

    public static IdentifierType fromValue(String value) {
        for (IdentifierType type : IdentifierType.values()) {
            if (type.identifierType.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
