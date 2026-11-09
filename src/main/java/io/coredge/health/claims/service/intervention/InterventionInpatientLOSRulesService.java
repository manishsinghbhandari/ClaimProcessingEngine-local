package io.coredge.health.claims.service.intervention;

import io.coredge.health.claims.db.entity.PackageMaster;
import io.coredge.health.claims.db.entity.intervention.InterventionInpatientLosRule;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.PackageMasterRepository;
import io.coredge.health.claims.db.repositories.intervention.InterventionInpatientLosRuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class InterventionInpatientLOSRulesService {

    @Autowired private InterventionInpatientLosRuleRepository losRuleRepository;
    @Autowired private BenefitsMasterRepository benefitsRepository;
    @Autowired private PackageMasterRepository packageMasterRepository;


    /**
     * Check if claim requires medical review based on inpatient LOS rules
     * @param serviceCategory The service category (inpatient, outpatient, emergency)
     * @param lengthOfStay The calculated length of stay
     * @param providerLevel The provider level
     * @param interventionCode The intervention code
     * @param packageCode The package code
     * @return true if medical review is required, false otherwise
     */
    public boolean checkInpatientLosRule(String serviceCategory, int lengthOfStay, String providerLevel,
                                         String interventionCode, String packageCode, String providerAgent) {

        log.info("Checking inpatient LOS rule - serviceCategory: {}", serviceCategory);

        // Only check for inpatient claims
        if (!"inpatient".equalsIgnoreCase(serviceCategory)) {
            return false;
        }

        try {
            Optional<PackageMaster> packageMaster = packageMasterRepository.findByCode(packageCode);
            if (packageMaster.isEmpty()) {
                return false;
            }
            log.info("Package master found for code: {}", packageCode);

            log.info(
                    "Calling rule mapping against packageCode: {}, LOS: {}, Level: {}, Action: {}, ProviderAgent: {}",
                    packageCode, lengthOfStay, providerLevel, providerAgent
            );

            PackageMaster master = packageMaster.get();
            Optional<InterventionInpatientLosRule> ruleOptional = losRuleRepository.findRuleByParams(
                    master.getId(),
                    providerLevel.toUpperCase(),
                    lengthOfStay,
                    getOwnership(providerAgent)
            );

            if (ruleOptional.isPresent()) {
                InterventionInpatientLosRule rule = ruleOptional.get();
                log.info("Inpatient LOS rule matched for intervention: {}, LOS: {}, Level: {}, Action: {}",
                        interventionCode, lengthOfStay, providerLevel, rule.getActionStatus());

                // Check if action status requires medical review
                return rule.getActionStatus() != null &&
                        "MEDICAL_REVIEW".equalsIgnoreCase(rule.getActionStatus());
            }else {
                log.info("No inpatient LOS rule matched for intervention");
            }

            return false;
        } catch (Exception e) {
            log.error("Error checking inpatient LOS rules for intervention: {}. Error: {} - line: {}",
                    interventionCode, e.getMessage(), e.getStackTrace()[0].getLineNumber());
            return false;
        }
    }

    /**
     * Determine ownership category based on provider agent string
     * @param providerAgent The provider agent string
     * @return Ownership category (GOK, Faith-Based, Private, Unknown)
     */
    private String getOwnership(String providerAgent) {
        if (providerAgent == null || providerAgent.trim().isEmpty()) {
            return "Unknown";
        }

        String agentLower = providerAgent.toLowerCase();
        if (agentLower.contains("ministry") || agentLower.contains("gok") || agentLower.contains("government")) {
            return "GOK";
        }
        if (agentLower.contains("faith") || agentLower.contains("catholic") ||
                agentLower.contains("christian") || agentLower.contains("episcopal")) {
            return "Faith-Based";
        }
        if (agentLower.contains("private") || agentLower.contains("ngo") ||
                agentLower.contains("company")) {
            return "Private";
        }

        return "Unknown";
    }
}
