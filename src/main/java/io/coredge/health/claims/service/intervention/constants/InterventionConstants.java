package io.coredge.health.claims.service.intervention.constants;

import java.util.List;
import java.util.Set;

public class InterventionConstants {

    private InterventionConstants() {

    }

    public static final String HOUSEHOLD = "household";
    public static final String INDIVIDUAL = "individual";
    public static final String PERITONEAL_DIALYSIS = "PeritonealDialysis";
    public static final String ACUTE_DIALYSIS = "AcuteDialysisRule";
    public static final String INPATIENT = "inpatient";

    public static final String REHAB = "RehabRule";
    public static final String DURATION = "duration";

    public static final String PER_DIEM = "PerDiem";

    public static final String SPECIALIZED = "Specialized";

    public static final String MAJOR = "Major";

    public static final String MINOR = "Minor";

    public static final String RENAL_CARE = "RenalCare";

    public static final String FACILITY_LEVEL = "facility-level";

    public static final String EYE_CARE = "EyeCare";

    public static final int DAYS_PER_WEEK = 7;
    public static final int WEEKS_PER_YEAR = 52;
    public static final int YEAR_DAYS = DAYS_PER_WEEK * WEEKS_PER_YEAR;


    public static final List<String> SURGICAL_CATEGORIES = List.of(SPECIALIZED, MAJOR, MINOR);


    public static final Set<String> APPROVED_STATUS = Set.of(
            "APPROVED",
            "SENT_FOR_PAYMENT_PROCESSING",
            "PAYMENT_COMPLETED",
            "MEDICAL_REVIEW"
    );

    public static final Set<String> DECLINED_STATUS = Set.of(
            "CLARIFICATION_AFTER_PAYER_CHECKS",
            "CLINICAL_REVIEW",
            "SENT_TO_SURVEILLANCE",
            "UNDER_COMMITTEE_REVIEW",
            "SENT_BACK",
            "REJECTED"
    );
}
