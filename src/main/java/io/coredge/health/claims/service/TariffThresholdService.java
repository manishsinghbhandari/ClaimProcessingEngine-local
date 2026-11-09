package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.BenefitsMaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles tariff threshold calculations for interventions that have a split tariff structure.
 */
@Slf4j
@Service
@RefreshScope
public class TariffThresholdService {

    private static final Pattern LEVEL_NUMBER_PATTERN = Pattern.compile("(\\d+)");

    @Value("#{'${tariff.threshold.package-prefixes:}'.split(',')}")
    private List<String> packagePrefixes;

    @Value("${tariff.threshold.threshold-days:0}")
    private int thresholdDays;

    @Value("#{'${tariff.threshold.excluded-interventions:}'.split(',')}")
    private List<String> excludedInterventions;

    @PostConstruct
    public void init() {
        log.info("TariffThreshold config loaded: prefixes={}, thresholdDays={}, excludedInterventions={}",
                packagePrefixes, thresholdDays, excludedInterventions);
    }

    /**
     * Calculate the approved amount applying tariff threshold logic if applicable.
     */
    public Optional<BigDecimal> calculateApprovedAmount(String interventionCode, int lengthOfStay,
                                                        String providerLevel, BenefitsMaster benefit) {
        if (interventionCode == null || benefit == null) {
            return Optional.empty();
        }

        Optional<String> matchingPrefix = findMatchingPrefix(interventionCode);
        if (matchingPrefix.isEmpty()) {
            return Optional.empty();
        }

        if (isExcluded(interventionCode)) {
            log.debug("Intervention {} is excluded from tariff threshold rule", interventionCode);
            return Optional.empty();
        }

        if (thresholdDays <= 0) {
            log.warn("Threshold days is 0 or negative. Skipping tariff threshold.");
            return Optional.empty();
        }

        // Parse initial tariff from the flat 'tariffs' column
        BigDecimal initialTariff = parseInitialTariff(benefit);
        if (initialTariff == null || initialTariff.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("No valid initial tariff found for intervention {}. tariffs={}", interventionCode, benefit.getTariffs());
            return Optional.empty();
        }

        int initialDays = Math.min(thresholdDays, lengthOfStay);
        int rebateDays = Math.max(0, lengthOfStay - thresholdDays);
        BigDecimal amount = initialTariff.multiply(BigDecimal.valueOf(initialDays));

        if (rebateDays > 0) {
            BigDecimal rebateTariff = getRebateTariff(benefit, providerLevel);
            if (rebateTariff == null || rebateTariff.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("No valid rebate tariff for intervention {} at provider level {}. level_X_tariff not set?",
                        interventionCode, providerLevel);
                return Optional.empty();
            }
            amount = amount.add(rebateTariff.multiply(BigDecimal.valueOf(rebateDays)));
        }

        log.info("Tariff threshold applied: intervention={}, LOS={}, thresholdDays={}, initialDays={}, rebateDays={}, " +
                        "initialTariff={}, rebateTariff={}, calculatedAmount={}",
                interventionCode, lengthOfStay, thresholdDays, initialDays, rebateDays,
                initialTariff, rebateDays > 0 ? getRebateTariff(benefit, providerLevel) : "N/A", amount);

        return Optional.of(amount);
    }

    /**
     * Parse the flat tariff value from the 'tariffs' String column.
     */
    private BigDecimal parseInitialTariff(BenefitsMaster benefit) {
        String tariffStr = benefit.getTariffs();
        if (tariffStr == null || tariffStr.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(tariffStr.trim());
        } catch (NumberFormatException e) {
            log.warn("Cannot parse tariffs value '{}' for intervention {}", tariffStr, benefit.getInterventionCode());
            return null;
        }
    }

    /**
     * Get the rebate tariff from level_X_tariff columns based on provider level.
     */
    private BigDecimal getRebateTariff(BenefitsMaster benefit, String providerLevel) {
        Integer levelNumber = extractProviderLevelNumber(providerLevel);
        if (levelNumber == null) {
            log.warn("Could not extract provider level number from: {}", providerLevel);
            return null;
        }

        Integer tariffValue = getTariffByLevel(benefit, levelNumber);
        if (tariffValue == null || tariffValue <= 0) {
            return null;
        }
        return BigDecimal.valueOf(tariffValue);
    }

    /**
     * Extract numeric provider level from provider level string.
     * Examples: "Level 4" -> 4, "level-5" -> 5, "level 6a" -> 6
     */
    private Integer extractProviderLevelNumber(String providerLevel) {
        if (providerLevel == null || providerLevel.isBlank()) {
            return null;
        }
        Matcher matcher = LEVEL_NUMBER_PATTERN.matcher(providerLevel);
        if (matcher.find()) {
            int level = Integer.parseInt(matcher.group(1));
            if (level >= 2 && level <= 6) {
                return level;
            }
        }
        return null;
    }

    private Integer getTariffByLevel(BenefitsMaster benefits, Integer levelNumber) {
        return switch (levelNumber) {
            case 2 -> benefits.getLevel2Tariff();
            case 3 -> benefits.getLevel3Tariff();
            case 4 -> benefits.getLevel4Tariff();
            case 5 -> benefits.getLevel5Tariff();
            case 6 -> benefits.getLevel6Tariff();
            default -> null;
        };
    }

    /**
     * Find the matching package prefix for a given intervention code.
     */
    private Optional<String> findMatchingPrefix(String interventionCode) {
        if (interventionCode == null || packagePrefixes == null) return Optional.empty();
        return packagePrefixes.stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .filter(prefix -> interventionCode.toUpperCase().startsWith(prefix.toUpperCase()))
                .findFirst();
    }

    private boolean isExcluded(String interventionCode) {
        if (excludedInterventions == null) return false;
        return excludedInterventions.stream()
                .map(String::trim)
                .anyMatch(excluded -> excluded.equalsIgnoreCase(interventionCode));
    }
}
