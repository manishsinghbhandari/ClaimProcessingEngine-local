package io.coredge.health.claims.models;

import io.coredge.health.claims.enums.ReviewPriorityEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReviewPriorityDTO {
    private ReviewPriorityEnum category;
    private String name;
    private String value;
    private Integer priority;
}
