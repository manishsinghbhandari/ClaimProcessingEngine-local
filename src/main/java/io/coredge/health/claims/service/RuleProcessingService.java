package io.coredge.health.claims.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.enums.AdjudicationParamsMessage;
import io.coredge.health.claims.enums.BenefitMasterSearchType;
import io.coredge.health.claims.models.RuleInputDTO;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_LOS;

@Slf4j
@Service
public class RuleProcessingService {
    private final BenefitsMasterRepository benefitsRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RuleProcessingService(BenefitsMasterRepository benefitsRepository) {
        this.benefitsRepository = benefitsRepository;
    }

    public Map<String, RuleInputDTO> processAdjudicationParamsRule(ClaimLineItem lineItem) throws JsonProcessingException {
        var rulesMap = objectMapper.readValue(lineItem.getAdjudicationRule(), new TypeReference<Map<String, Object>>() {});
        var inputsMap = objectMapper.readValue(lineItem.getAdjudicationParams(), new TypeReference<Map<String, Object>>() {});
        var processedRules = processRules(rulesMap, inputsMap, lineItem.getJustification());
        if (lineItem.isPmfItem() && processedRules.containsKey("claimAmount")) {
            var dto = processedRules.get("claimAmount");

            BigDecimal pmfAmount = Objects.requireNonNullElse(lineItem.getPmfAmount(), BigDecimal.ZERO);
            dto.setPmsfAmount(pmfAmount);
            dto.setShifAmount(lineItem.getNetValue().subtract(pmfAmount));

            if (Constants.ClaimLineItemStatus.PASSED.equals(lineItem.getStatus())) {
                dto.setResult("PASS");
                dto.setRuleSummary(null);
                dto.setReason(null);
            }
        }
        var isShaAndEccifCoverage = benefitsRepository.existsByInterventionCodeAndExactAppliesToSchemes(
                lineItem.getCode(), BenefitMasterSearchType.CoverageType.SHA_ECCIF_PMSF.getValue());

        if (isShaAndEccifCoverage && processedRules.containsKey("individualUsage")) {
            var individualUsageDTO = processedRules.get("individualUsage");
            if (lineItem.getEccifAmount() != null) {
                String amountStr = individualUsageDTO.getRule().toString();
                BigDecimal shifAmount = new BigDecimal(amountStr.replaceAll("[^\\d]", ""));
                BigDecimal input = new BigDecimal(individualUsageDTO.getInput().toString());

                individualUsageDTO.setEccifAmount(input.subtract(shifAmount));
                individualUsageDTO.setShifAmount(shifAmount);
            }

            if (Constants.ClaimLineItemStatus.PASSED.equals(lineItem.getStatus())) {
                individualUsageDTO.setResult("PASS");
                individualUsageDTO.setRuleSummary(null);
                individualUsageDTO.setReason(null);
            }
        }
        return processedRules;
    }


    public Map<String, RuleInputDTO> processRules(Map<String, Object> rules, Map<String, Object> inputs, String justification) {
        Map<String, RuleInputDTO> combinedRules = new HashMap<>();
        rules.forEach((key, rule) -> {
            if (!"code".equals(key)) {
                RuleInputDTO ruleInputDTO = new RuleInputDTO();
                if (rule instanceof String && ((String) rule).contains(",")) {
                    List<String> ruleList = Arrays.asList(((String) rule).split(","));
                    ruleInputDTO.setRule(ruleList);
                } else {
                    ruleInputDTO.setRule(rule);
                }
                if (inputs.containsKey(key)) {
                    Object input = inputs.get(key);
                    ruleInputDTO.setInput(input);
                    assert rule instanceof String;
                    String result = validateRule((String) rule, input, inputs);
                    if (result.equalsIgnoreCase("FAIL")) {
                        ruleInputDTO.setReason(getReasonSummary(key));
                    }
                    ruleInputDTO.setResult(result);
                    if ("claimAmount".equalsIgnoreCase(key) && ((String) rule).contains("*")) {
                        ruleInputDTO.setRuleSummary(getAmountRuleSummary((String) rule, inputs));
                    }
                } else {
                    if (!"Auto Approve".equalsIgnoreCase(key)) {
                        ruleInputDTO.setInput("");
                        ruleInputDTO.setResult("FAIL");
                    } else {
                        processAutoApprove(justification, key, rule, ruleInputDTO);
                    }
                }
                combinedRules.put(key, ruleInputDTO);

            }
        });
        return combinedRules;
    }

    public Map<String, RuleInputDTO> processRules(Map<String, String> rules, Map<String, Object> inputs) {
        Map<String, RuleInputDTO> combinedRules = new HashMap<>();

        rules.forEach((key, rule) -> {
            if (!"code".equals(key)) {
                RuleInputDTO ruleInputDTO = new RuleInputDTO();

                // Convert rule string to list if it contains commas
                ruleInputDTO.setRule(rule.contains(",") ? Arrays.asList(rule.split(",")) : rule);

                if (inputs.containsKey(key)) {
                    Object input = inputs.get(key);
                    ruleInputDTO.setInput(input);

                    String result = validateRule(rule, input, inputs);
                    ruleInputDTO.setResult(result);

                    if ("FAIL".equalsIgnoreCase(result)) {
                        ruleInputDTO.setReason(getReasonSummary(key));
                    }

                    if ("claimAmount".equalsIgnoreCase(key) && rule.contains("*")) {
                        ruleInputDTO.setRuleSummary(getAmountRuleSummary(rule, inputs));
                    }
                } else if (!"Auto Approve".equalsIgnoreCase(key)) {
                    ruleInputDTO.setInput("");
                    ruleInputDTO.setResult("FAIL");
                }

                combinedRules.put(key, ruleInputDTO);
            }
        });

        return combinedRules;
    }


    private static void processAutoApprove(String justification, String key, Object rule, RuleInputDTO ruleInputDTO) {
        boolean isAutoApproved = StringUtils.hasText(justification) && justification.contains("Auto Approved");

        ruleInputDTO.setInput(String.valueOf(isAutoApproved));
        ruleInputDTO.setRuleSummary(StringUtils.hasText(justification) ? "Justification: " + justification : "");
        ruleInputDTO.setRule(rule);
        ruleInputDTO.setResult(isAutoApproved ? "PASS" : "FAIL");
    }

    private String validateRule(String rule, Object input, Map<String, Object> inputs) {
        // No Rules should be pass
        if (rule.isEmpty()) return "PASS";

        try {
            if (rule.contains(">=") || rule.contains("<=") || rule.contains(">") || rule.contains("<") || rule.contains("=")) {
                String operator = rule.contains(">=") ? ">=" : rule.contains("<=") ? "<=" : rule.contains(">") ? ">" : "<";
                if (rule.contains("*")) { // Handle dynamic rules
                    return handleDynamicRules(operator, rule, input, inputs);
                }
                double value = Double.parseDouble(rule.replace(operator, "").trim());
                double inputValue = Double.parseDouble(input.toString());
                return switchOperator(operator, inputValue, value);
            } else if (rule.contains(",")) {
                return rule.toLowerCase().contains(input.toString().toLowerCase()) ? "PASS" : "FAIL";
            } else if (rule.equalsIgnoreCase(input.toString())) {
                return "PASS";
            } else if (rule.contains("..")) {
                if (rule.contains("[")) {
                    rule = sanitizeAgeRule(rule);
                }// Handle range
                String[] rangeParts = rule.split("\\.\\.");
                if (rangeParts.length == 2) {
                    double min = Double.parseDouble(rangeParts[0].trim());
                    double max = Double.parseDouble(rangeParts[1].trim());
                    double inputValue = Double.parseDouble(input.toString());
                    return (inputValue >= min && inputValue <= max) ? "PASS" : "FAIL";
                }
            }
        } catch (Exception e) {
            log.warn("Exception in validateRule: {}", e.getMessage(), e);
            return "FAIL";
        }
        return "FAIL";
    }

    private @NotNull String handleDynamicRules(String operator, String rule, Object input, Map<String, Object> inputs) {
        String[] parts = rule.split("\\*");
        String variable = parts[0].replace("<=", "").trim();
        double multiplier = Double.parseDouble(parts[1].trim());
        double value = Double.parseDouble(inputs.get(variable).toString()) * multiplier;
        double inputValue = Double.parseDouble(input.toString());
        return switchOperator(operator, inputValue, value);
    }

    private String switchOperator(String operator, double inputValue, double value) {
        return switch (operator) {
            case ">=" -> inputValue >= value ? "PASS" : "FAIL";
            case "<=" -> inputValue <= value ? "PASS" : "FAIL";
            case ">" -> inputValue > value ? "PASS" : "FAIL";
            case "<" -> inputValue < value ? "PASS" : "FAIL";
            case "=" -> inputValue == value ? "PASS" : "FAIL";
            default -> "FAIL";
        };
    }

    private @NotNull String getAmountRuleSummary(String rule, Map<String, Object> inputs) {
        rule = rule.replace("<=", "");
        String[] parts = rule.split("\\*");
        String variable = parts[0].trim();
        double multiplier = Double.parseDouble(parts[1].trim());
        String los = inputs.get(variable).toString();
        double value = Double.parseDouble(los) * multiplier;
        return "los=" + los + "," + rule.replace("los", los) + "=" + value;
    }


    private String getReasonSummary(String key) {
        return Objects.requireNonNull(AdjudicationParamsMessage.getMessage(key)).replaceAll("Rule: %s, Input: %s", "").trim();
    }

    private String sanitizeAgeRule(String rule) {
        return rule.replaceAll("[\\[\\]]", "").trim();
    }
}
