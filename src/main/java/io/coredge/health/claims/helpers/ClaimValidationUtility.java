package io.coredge.health.claims.helpers;

import io.coredge.health.claims.service.BenefitMasterService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Period;

/**
 *
 */
@Slf4j
@UtilityClass
public class ClaimValidationUtility {

    /**
     * Function to check for Valid Billable Period
     */
    public boolean isValidBillablePeriod(Period billablePeriod) {
        if (billablePeriod == null) {
            return false;
        }

        LocalDate startDate = (billablePeriod.hasStart()) ? convertToLocalDate(billablePeriod.getStart()) : null;
        LocalDate endDate = (billablePeriod.hasEnd()) ? convertToLocalDate(billablePeriod.getEnd()) : null;

        // Validation : either date should not be null/empty
        if (startDate == null || endDate == null) {
            return false;
        }

        // Validation : Start date should not be after the end date
        if (startDate.isAfter(endDate)) {
            return false;
        }

        // Validation : Billable period must not be in the future
        LocalDate today = LocalDate.now();
        return !endDate.isAfter(today);

        // Validation: Billable period must be at least a month-long
        //        long periodBetween = ChronoUnit.DAYS.between(startDate, endDate);
        //        if (periodBetween < 30) {
        //            return false;
        //        }

        //        // Validation 3: Billable period must not exceed 1 year
        //        if (endDate.isAfter(startDate.plusYears(1))) {
        //            return "Billable period cannot exceed 1 year.";
        //        }
        //        no errors
    }

    private LocalDate convertToLocalDate(Date dateToConvert) {
        return dateToConvert.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Method to validate productOrService codes inside Claim items Return a list of string invalid
     * codes
     */
    public List<String> listInvalidCodes(Claim claim, BenefitMasterService benefitMasterService) {
        if (claim.getItem() == null) {
            return Collections.emptyList(); // No items, so no invalid codes
        }
        /*
         * Ensure productOrService exists , Stream over coding objects , Get the code, Filter invalid
         * codes, Collect them*
         */
        return claim.getItem().stream().filter(item -> item.getProductOrService() != null).flatMap(
                item -> item.getProductOrService().getCoding().stream()).map(Coding::getCode).filter(
                        code -> !isValidProductOrServiceCode(
                                code, benefitMasterService)).collect(Collectors.toList());
    }

    /**
     * Method to check if claim has duplicate Intervention codes.
     *
     * @return true if it has duplicate interventions
     */
    public boolean hasDuplicateInterventions(Claim claim) {
        List<String> claimInterventionCodes = new ArrayList<>();
        for (Claim.ItemComponent item : claim.getItem()) {
            String interventionCode = item.getProductOrService().getCoding().getFirst().getCode();
            if (claimInterventionCodes.contains(interventionCode)) {
                return true;
            }
            claimInterventionCodes.add(interventionCode);
        }
        return false;
    }

    // Utility method to check the validity of productOrService codes
    public boolean isValidProductOrServiceCode(String code, BenefitMasterService benefitMasterService) {
        return benefitMasterService.existsByInterventionCode(code);
    }

    //Check Null
    public boolean hasNullProductOrServiceCode(Claim claim) {
        if (claim.getItem() == null) {
            return true;
        }
        return claim.getItem().stream().anyMatch(item -> item.getProductOrService() == null || item.getProductOrService().getCoding().stream().anyMatch(coding -> coding.getCode() == null || coding.getCode().isEmpty()));
    }

    //Check if claim has AtLeast one attachment
    public static boolean hasAtLeastOneAttachment(Claim claim) {
        if (claim.getSupportingInfo() == null || claim.getSupportingInfo().isEmpty()) {
            return false;
        }
        return claim.getSupportingInfo().stream().filter(Objects::nonNull) // Filter out null entries
                .anyMatch(info -> info.getValueAttachment() != null && info.getValueAttachment().getUrl() != null && !info.getValueAttachment().getUrl().trim().isEmpty() // Ensure URL is non-null and non-empty
                        && info.getValueAttachment().getTitle() != null);
    }

    public static boolean isPreAuth(Claim claim) {
        return claim.hasUse() && claim.getUse().equals(Claim.Use.PREAUTHORIZATION);
    }
}
