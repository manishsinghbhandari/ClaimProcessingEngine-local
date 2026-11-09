package io.coredge.health.claims.models.master;

import jakarta.persistence.Column;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class ProviderResponse {
    private Long id;
    private String identifier;
    private String mflCode;
    private String fidCode;
    private String name;
    private String regNo;
    private String level;
    private Date lastUpdated;
    private String facilityType;
    private String alias;
    private String addressLine;
    private String city;
    private String district;
    private String state;
    private String country;
    private String entityType;
    private String licenseStatus;
    private String licenseNumber;
    private String county;


    // Computed property for ownership
    public String getOwnership() {
        if (entityType != null) {
            if (entityType.toLowerCase().contains("ministry") || entityType.toLowerCase().contains("gok")) {
                return "GoK";
            } else if (entityType.toLowerCase().contains("faith") || entityType.toLowerCase().contains("catholic") || entityType.toLowerCase().contains("christian") || entityType.toLowerCase().contains("episcopal")) {
                return "Faith-based";
            } else {
                return "Unknown";
            }
        }
        return null; // or a default value like "unknown" if preferred
    }

}
