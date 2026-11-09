package io.coredge.health.claims.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BenefitDTO {
    private Long id ; // Assuming you want to include an ID field
    private String name ; // Assuming you want to include a name field
    private String code ; // Assuming you want to include a code field

    // Add other fields you want to expose

    // Constructors
    public BenefitDTO() {}

    public BenefitDTO(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public BenefitDTO(Long id, String name, String code) {
        this.id = id;
        this.name = name;
        this.code = code;
    }
//
//    // Getters and setters
//    public Long getId() { return id; }
//    public void setId(Long id) { this.id = id; }
//    public String getName() { return name; }
//    public void setName(String name) { this.name = name; }
//    public String getCode() { return code; }
//    public void setCode(String code) { this.code = code; }
}