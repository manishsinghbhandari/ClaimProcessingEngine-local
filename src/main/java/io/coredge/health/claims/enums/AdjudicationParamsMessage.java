package io.coredge.health.claims.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Getter
public enum AdjudicationParamsMessage {

    AGE("age", "Age", "Invalid_Age","The provided age does not meet the adjudication rules. Rule: %s, Input: %s"),
    //AUTO_APPROVE("Auto Approve", "Auto Approve", "", "The request for auto-approval is not allowed. Rule: %s, Input: %s"),
    LOS("los", "Length of Stay", "Invalid_LOS","The length of stay does not satisfy the rule. Rule: %s, Input: %s"),
    //CODE("code", "Code", "The provided code does not meet the required rule. Rule: %s, Input: %s"),
    SEQUENCE("sequence", "Sequence", "Invalid_Sequence","The sequence provided does not comply with the rule. Rule: %s, Input: %s"),
    GENDER("gender", "Gender", "Invalid_Gender","The provided gender does not match the adjudication rules. Rule: %s, Input: %s"),
    CLAIM_AMOUNT("claimAmount", "Claim Amount", "Claim_Amount_Excess","The claim amount exceeds the allowed limit as per the adjudication rules. Rule: %s, Input: %s"),
    HOUSE_HOLD_USAGE("houseHoldUsage", "Household Usage", "HH_Usage_Excess","The household usage has exceeded the allowed limit. Rule: %s, Input: %s"),
    INDIVIDUAL_USAGE("individualUsage", "Individual Usage", "Individual_Usage_Excess","The individual usage has exceeded the allowed limit. Rule: %s, Input: %s"),
    SERVICE_CATEGORY("serviceCategory", "Service Category", "Invalid_Accesspoint","The provided service category is not applicable based on the rules. Rule: %s, Input: %s"),
    FACILITY_CATEGORY("facilityCategory", "Facility Category", "Invalid_Level","The facility category does not align with the adjudication rules. Rule: %s, Input: %s"),
    INTERVENTION_USAGE_COUNT("interventionUsageCount", "Intervention Usage Count", "Service_Usage_Excess","The usage count exceeds the allowed limit as per the adjudication rules. Rule: %s, Input: %s"),
    PRE_AUTHENTICATION("hasPreAuth", "Pre-Authentication", "Missing_Preauth","The claim requires a pre-authorization, but none was found. Rule: %s, Input: %s"),
    PRE_AUTH_AMOUNT("preAuthAmount", "Pre-Authentication Amount", "Claim_Amount_Exceeds_Preauth","The pre-authorization Approved Amount is less than Claim Amount. Rule: %s, Input: %s"),
    PATIENT("patient" , "Patient" , "Patient_Not_Found","Patient Not Found with the Id %s provided ");

    private final String key;
    private final String label;
    private final String rule;
    private final String message;

    AdjudicationParamsMessage(String key, String label,String rule, String message) {
        this.key = key;
        this.label = label;
        this.rule = rule;
        this.message = message;
    }

    @JsonCreator
    public static AdjudicationParamsMessage fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return Arrays.stream(AdjudicationParamsMessage.values()).filter(temp -> temp.name().equalsIgnoreCase(value) || temp.getKey().equalsIgnoreCase(value)|| temp.getLabel().equalsIgnoreCase(value)).findAny().orElse(null);
    }

    public static String createMessage(AdjudicationParamsMessage message , String rule ,String input) {
        if (message != null) {
            return String.format(message.getMessage(),rule , input);
        }
        return "-";
    }

    public static String getMessage(String key) {
        for (AdjudicationParamsMessage param : AdjudicationParamsMessage.values()) {
            if (param.getKey().equalsIgnoreCase(key)) {
                return param.getMessage();
            }
        }
        return null;
    }
}
