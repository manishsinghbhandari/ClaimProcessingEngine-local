package io.coredge.health.claims.enums;

import io.coredge.health.claims.db.entity.ReviewPriority;
import lombok.Getter;

import java.util.function.Function;

@Getter
public enum ReviewPriorityEnum {
    PACKAGE_SUB_PACKAGE("Package / Sub-Package" , (ReviewPriority priority) -> "('"+priority.getCategoryValue()+"' IS NULL OR item.code ILIKE '"+priority.getCategoryValue()+"%')"),
    FACILITY("Facility" , (ReviewPriority priority)-> "('"+priority.getCategoryValue()+"' IS NULL OR LOWER(p.fid_code) = '" + priority.getCategoryValue().toLowerCase()+"')"),
    OWNERSHIP("Ownership" , (ReviewPriority priority)-> "('"+priority.getCategoryValue()+"' IS NULL OR LOWER(p.facility_agent) = '" + priority.getCategoryValue().toLowerCase()+"')"),
    LEVEL("Level" , (ReviewPriority priority)-> "('"+priority.getCategoryValue()+"' IS NULL OR LOWER(p.level) = '" + priority.getCategoryValue().toLowerCase()+"')"),
    ;

    private final String name;
    private final Function<ReviewPriority, String> function;

    ReviewPriorityEnum(String name , Function<ReviewPriority, String> function) {
        this.name = name;
        this.function = function;
    }
}
