package io.coredge.health.claims.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SchemeDetailDTO {

    private Long id;

    private Integer autoNumber;

    private String autoCode;

    @NotBlank
    private String systemCode;

    private String uuid;

    @NotBlank
    private String name;

    @NotNull
    private Date startDate;

    private Date endDate;

    @Min(1)
    private Integer familySize;

    @NotNull
    private List<Long> jobGroupIds;

    @NotNull
    private List<SchemeBenefitLimitDTO> benefitLimits;
}