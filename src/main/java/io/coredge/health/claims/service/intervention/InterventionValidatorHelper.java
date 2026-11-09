package io.coredge.health.claims.service.intervention;

import io.coredge.health.claims.utils.FhirHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.sparql.function.library.leviathan.log;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@Slf4j
public class InterventionValidatorHelper {

    public static final String IP = "ip";
    public static final String OP = "op";
    public static final String INPATIENT = "inpatient";
    public static final String OUTPATIENT = "outpatient";

    public List<String> extractAllInterventionCodes(Claim claim) {
        if (!claim.hasItem()) {
            return Collections.emptyList();
        }

        return claim.getItem().stream()
                .map(item -> item.getProductOrService().getCodingFirstRep().getCode())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public LocalDate extractServicedDateFromClaim(Claim claim, String shaCode) {
        return claim.getItem().stream()
                .filter(item -> {
                    Coding coding = item.getProductOrService().getCodingFirstRep();
                    return coding != null && shaCode.equals(coding.getCode());
                })
                .map(Claim.ItemComponent::getServiced)
                .filter(DateType.class::isInstance)
                .map(DateType.class::cast)
                .map(dateType -> dateType.getValue()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No serviced date found in claim for code: " + shaCode));
    }

    public Date startBillablePeriod(Claim claim) {
        if (claim.hasItem() && claim.hasBillablePeriod()) {
            return claim.getBillablePeriod().getStart();
        }
        return null;
    }

    public Date endBillablePeriod(Claim claim) {
        if (claim.hasItem() && claim.hasBillablePeriod()) {
            return claim.getBillablePeriod().getEnd();
        }
        return null;
    }

    /**
     *
     * @param claim
     * @return
     */
    public String patientSubtype(Claim claim) {
        if (claim.hasSubType() && claim.getSubType().hasCoding()) {
            String code = claim.getSubType().getCodingFirstRep().getCode();
            if (IP.equalsIgnoreCase(code)) {
                return INPATIENT;
            } else if (OP.equalsIgnoreCase(code)) {
                return OUTPATIENT;
            }
        }
        return StringUtils.EMPTY;
    }

    /**
     * Calculates the total net value of all claim items.
     * Each item's net value is computed by multiplying its net by its quantity.
     *
     * @param claim The claim object containing a list of items.
     * @return The total net value of the claim as a BigDecimal.
     */
    public BigDecimal getClaimsTotal(Claim claim) {
        return claim.hasTotal() ? claim.getTotal().getValue() : BigDecimal.ZERO;
    }

    public List<String> extractInterventionCodes(Claim claim) {
        if (claim == null || !claim.hasItem()) {
            return Collections.emptyList();
        }

        return claim.getItem()
                .stream()
                .filter(item -> item.hasProductOrService()
                        && item.getProductOrService().hasCoding())
                .map(item -> item.getProductOrService()
                        .getCodingFirstRep()
                        .getCode())
                .toList();
    }

    public int calculateLos(Claim claim) {

        Date admission = startBillablePeriod(claim);
        Date discharge = endBillablePeriod(claim);
        log.info("Calculating Length of Stay (LOS) for claimId={}", claim.getIdElement().getIdPart());
        LocalDate startDate = FhirHelper.convertToLocalDate(admission);
        LocalDate endDate = FhirHelper.convertToLocalDate(discharge);
        log.debug("Admission date: {}, Discharge date: {}", startDate, endDate);

        if (startDate == null || endDate == null) {
            return 0;
        }

        if (!endDate.isAfter(startDate)) {
            log.warn("Invalid LOS for claimId={} because discharge date {} is not after admission date {}",
                    claim.getIdElement().getIdPart(), endDate, startDate);
            return 0;
        }
        int los = (int) ChronoUnit.DAYS.between(startDate, endDate);
        log.info("Calculated LOS={} days for claimId={}", los, claim.getIdElement().getIdPart());
        return los;
    }
}
