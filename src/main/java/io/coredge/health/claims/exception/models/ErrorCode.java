package io.coredge.health.claims.exception.models;

/**
 * Error codes for the application.
 * 
 * Parent error codes are prefixed (e.g., CLAIM-422 or PERMISSION-403).
 * Each error code includes a code and a description.
 * 
 * Author: Rajesh Kr <rajeshk@coredge.io>
 */
public enum ErrorCode {

  // General Errors
  BAD_REQUEST("CLAIM-400", "The request is invalid."),
  UNAUTHORIZED("CLAIM-401", "Authentication is required."),
  THIRD_PARTY_API_ERROR("CLAIM-502", "Error occurred while calling external service."),

  // Intervention Errors
  CODE_DATA_NOT_FOUND("CLAIM-404", "Code data not found."),
  BUSINESS_EXCEPTION("CLAIM-422", "Request failed due to a business rule violation"),
  MISSING_CLAIM_IN_BUNDLE("CLAIM-410", "Claim is missing in the provided bundle."),
  MISSING_BILLABLE_PERIOD("CLAIM-411", "The billable period is missing."),
  INVALID_DATA("CLAIM-405", "Invalid data provided."),
  INVALID_INTERVENTION_CODES("CLAIM-423", "Intervention codes are invalid."),
  DUPLICATE_INTERVENTION_CODES("CLAIM-424", "Duplicate intervention codes detected."),
  DUPLICATE_CLAIM_ID("CLAIM-425", "Duplicate claim ID found."),
  MISSING_ATTACHMENTS("CLAIM-426", "Required attachments are missing."),
  NO_INTERVENTION_FOUND("CLAIM-427", "No intervention details found."),
  MISSING_PROVIDER_REF("CLAIM-428", "Provider reference is missing."),
  INVALID_CLAIM_SUB_TYPE("CLAIM-429", "Invalid claim subtype provided."),
  INVALID_WORK_FLOW("CLAIM-430", "Work flow exception occurred."),
  INVALID_CLAIM_TOTAL_AMOUNT("CLAIM-431", "Mismatch in claim total and items amount."),
  INVALID_INTERVENTION_SEQUENCE("CLAIM-422", "Invalid intervention sequence."),

  /**
   * Bundle reference and value must not blank
   * Claim/Pre-Authorization (Patient, Provider)
   */
  MISSING_PATIENT_REF("CLAIM-432", "Patient reference is missing."),
  MISSING_PROVIDER_ID("CLAIM-433", "No Matching Provider Id in system."),
  PRE_AUTH_NOT_IN_APPROVE_STATE("CLAIM-434", "Pre Auth Id is not in APPROVED state."),
  PRE_AUTH_NOT_EXISTS("CLAIM-435", "Pre Auth Id does not exists."),
  PRE_AUTH_ALREADY_EXISTS("CLAIM-436", "Pre Auth already exists for this claim"),
  SHA_CODES_MISMATCH("CLAIM-437", "Sha codes mismatch."),
  PATIENT_ASSOCIATION_CLAM("CLAIM-438", "Patient not associated with claim."),
  MULTIPLE_PRE_AUTH_CLAIM("CLAIM-439", "Multiple Pre Auth associated with claims."),
  UNIT_PRICE_MISMATCH("CLAIM-440", "Unit price mismatch."),
  MISMATCH_DATA("CLAIM-441", "Mismatch data."),
  VALIDATE_DATA_CANNOT_BLANK("CLAIM-442", "Value must not blank."),
  REQUIRED_FIELD_NOT_EXIST("CLAIM-443", "Required field not exist"),
  MFL_CODE_MISMATCH("CLAIM-444", "MFL code mismatch"),
  FID_CODE_MISMATCH("CLAIM-445", "FID code mismatch"),
  GENDER_MISMATCH("CLAIM-446", "Gender mismatch"),
  MISSING_REFERENCE("CLAIM-447", "Missing reference"),
  PRE_AUTH_EXPIRED("CLAIM-448", "Pre Auth has Expired as Per 7 days rule."),

  // System Errors
  SYSTEM_EXCEPTION("CLAIM-500", "An unexpected system error occurred."),
  DATABASE_EXCEPTION("CLAIM-503", "Database operation failed."),
  SHR_UNAVAILABLE("CLAIM-503", "SHR service is currently unavailable. Please try again later."),

  // Permission Errors
  RESOURCE_NOT_AUTHORIZED("P-403", "Access to the resource is not authorized."),
  RESOURCE_NOT_FOUND("R-404", "The specified resource was not found."),
  USER_NOT_FOUND("P-404", "The user was not found in the system."),
  BUSINESS_RULE_EXCEPTION(BUSINESS_EXCEPTION.code, "Request failed due to a business rule violation"),
  PATIENT_GENDER_MISMATCH("P-405", "Mismatch in patient gender information."),

  // Data Errors
  DATA_PROCESSING("CLAIM-202", "Request accepted and is still processing."),
  FIELD_REQUIRED("CLAIM-402", "The field value should not be empty"),
  UUID_MISSMATCH("CLAIM-406", "Please enter a valid UUID Number."),
  BENEFITS_NOT_FOUND("CLAIM-407", "Benefit ID Not Found."),
  SAME_VALUE_UPDATE_NOT_ALLOWED("CLAIM-408", "No changes detected. Update not allowed."),
  DUPLICATE_RECORD("CLAIM-409", "Duplicate score exists for decision %s with range %s-%s."),
  INVALID_AI_SCORE_SEQUENCE_EXCEPTION("CLAIM-449", "Invalid AI score sequence: %s"),
  INVALID_EMPTY_SCORES_EXCEPTION("CLAIM-450", "Scores list cannot be null or empty"),
  INVALID_WALLET_CODES("CLAIM-432", "Wallet creation exception occurred."),

  BILLABLE_BEFORE_POLICY_START("CLAIM-451", "Billable period cannot start before policy start date."),
  BILLABLE_AFTER_POLICY_END("CLAIM-452", "Billable period cannot exceed policy end date."),

  DUPLICATE_REJECTION_CATEGORY("CLAIM-453", "Category name or prefix already exists."),
  REASON_CATEGORY("CLAIM-454", "Category does not exists."),
  REJECTION_CODE("CLAIM-455", "Rejection code already exists."),
  REASON_CODE_NOT_FOUND("CLAIM-456", "Reason code not exists."),
  INVALID_PAYMENT_STATUS("CLAIM-457", "Invalid payment status for the current operation."),
  REASON_DESC("CLAIM-458", "Reason description already exists."),
  DUPLICATE_REASON_PREFIX("CLAIM-459", "Prefix already exists.");


  private final String code;
  private final String description;

  /**
   * Constructor for ErrorCode enum.
   *
   * @param code        the error code string
   * @param description the error description
   */
  ErrorCode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * Returns the error code string.
   *
   * @return the error code
   */
  public String code() {
    return this.code;
  }

  /**
   * Returns the error description.
   *
   * @return the error description
   */
  public String description() {
    return this.description;
  }
}
