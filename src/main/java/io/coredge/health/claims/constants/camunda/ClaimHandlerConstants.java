package io.coredge.health.claims.constants.camunda;

public final class ClaimHandlerConstants {

    // Prevent instantiation
    private ClaimHandlerConstants() {}

    /* -------------------------------
     Workflow Variable Keys
     ------------------------------- */

    public static final String IS_OVERNIGHT_STAY_FAILED = "isOvernightStayFailed";
    public static final String IS_POST_SAVE_VALIDATION_FAILED = "isPostSaveValidationFailed";
    public static final String VALIDATION_ERRORS = "validationErrors";
    public static final String IS_SAVE_VALIDATION = "isSaveValidation";

    /* -------------------------------
    Message Prefixes
    ------------------------------- */

    public static final String VALIDATION_FAILED_PREFIX = "Validation failed: ";
    public static final String INTERNAL_VALIDATION_ERROR_PREFIX = "Internal validation error: ";


    /* -------------------------------
      Boolean Values
      ------------------------------- */
    public static final Boolean TRUE = Boolean.TRUE;
    public static final Boolean FALSE = Boolean.FALSE;

}