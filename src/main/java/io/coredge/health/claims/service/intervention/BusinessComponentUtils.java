package io.coredge.health.claims.service.intervention;

import io.coredge.health.claims.models.intervention.ResourceType;
import io.coredge.health.claims.models.intervention.ComponentCategory;
import io.coredge.health.claims.models.intervention.ComponentType;
import io.coredge.health.claims.models.intervention.Severity;
import lombok.Getter;

import java.util.Arrays;

@Getter
public class BusinessComponentUtils {

    private static final String[] severity;
    private static final String[] ruleTypes;
    private static final String[] ruleCategory;
    private static final String[] resourceType;

    static {
        severity = Arrays.stream(Severity.values()).map(Severity::toString).toArray(String[]::new);
        ruleTypes = Arrays.stream(ComponentType.values()).map(ComponentType::toString).toArray(String[]::new);
        ruleCategory = Arrays.stream(ComponentCategory.values()).map(ComponentCategory::toString).toArray(String[]::new);
        resourceType = Arrays.stream(ResourceType.values()).map(ResourceType::toString).toArray(String[]::new);
    }

    public static String[] getData(Class<?> clazz) {

        if (clazz == Severity.class) {
            return severity;
        } else if (clazz == ComponentType.class) {
            return ruleTypes;
        } else if (clazz == ComponentCategory.class) {
            return ruleCategory;
        } else if (clazz == ResourceType.class) {
            return resourceType;
        }
        return null;
    }

}
