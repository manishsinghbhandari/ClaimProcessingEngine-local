package io.coredge.health.claims.constants.intervention;

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

    public static final String EYE_CARE = "EyeCare";

    public static final int DAYS_PER_WEEK = 7;
    public static final int WEEKS_PER_YEAR = 52;
    public static final int YEAR_DAYS = DAYS_PER_WEEK * WEEKS_PER_YEAR;
    public static final int DAYS_PER_WEEK_INCLUSIVE = 6;


    public static final int MINIMUM_LOS = 1;
    public static final int ALLOWED_MINOR_SURGERIES = 21;
    public static final int MINOR_SURGERY_COUNT = 1;

    public static final String PRE_AUTHORIZATION = "Preauthorization";


    public static final int ONE_YEAR = 1;
    public static final int ONE_DAY = 1;


    public static final String NO_RULES_APPLIED_MSG = "No applicable rules found for any of the SHA codes. Skipping validation.";


    public static final List<String> SURGICAL_CATEGORIES = List.of(SPECIALIZED, MAJOR, MINOR);


    public static final Set<String> APPROVED_STATUS = Set.of("PARTIAL_APPROVAL" , "PAYMENT_PARTIAL", "UNDER_INVESTIGATION" ,"UNDER_SURVEILLANCE" , "CLINICAL_REVIEW" , "MANUAL_REVIEW", "APPROVED", "SENT_FOR_PAYMENT_PROCESSING", "PAYMENT_COMPLETED", "MEDICAL_REVIEW");

    public static final Set<String> CLAIM_DECLINED_STATUS = Set.of("CLARIFICATION_AFTER_PAYER_CHECKS", "CLINICAL_REVIEW", "SENT_TO_SURVEILLANCE", "UNDER_COMMITTEE_REVIEW", "SENT_BACK", "REJECTED", "DECLINE");


    public static final Set<String> PRE_AUTH_DECLINED_STATUS = Set.of("SENT_BACK", "REJECTED", "CANCELED");

    public static final Set<String> PRE_AUTH_APPROVED_STATUS = Set.of("APPROVED", "CLINICAL_REVIEW");

    public static final List<String> OVERNIGHT_STAY_SHA_CODES = List.of("SHA-03-001", "SHA-03-002", "SHA-03-003", "SHA-03-004", "SHA-03-005", "SHA-07-001", "SHA-13-001");
}
