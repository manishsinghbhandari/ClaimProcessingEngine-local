package io.coredge.health.claims.exception.handler;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.coredge.health.claims.exception.app.*;
import io.coredge.health.claims.exception.base.AuthenticationException;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.base.DataBadRequestException;
import io.coredge.health.claims.exception.callback.MaxLimitExceededException;
import io.coredge.health.claims.exception.intervention.RuleViolationException;
import io.coredge.health.claims.exception.models.ApiError;
import io.coredge.health.claims.exception.models.ErrorCode;
import io.coredge.health.claims.exception.models.ErrorDetails;
import io.coredge.health.claims.exception.shr.ShrDataNotFoundException;
import io.coredge.health.claims.exception.shr.ShrFetchException;
import io.coredge.health.claims.exception.shr.ShrServiceUnavailableException;
import io.coredge.health.claims.payments.exception.PaymentBatchInvalidStatusException;
import io.coredge.health.claims.payments.exception.PaymentBatchMismatchException;
import io.coredge.health.claims.payments.exception.PaymentDataMismatchException;
import io.coredge.health.claims.payments.exception.PaymentInvalidStatusException;
import io.coredge.health.claims.payments.exception.PaymentMissingDataException;
import io.coredge.health.claims.payments.exception.PaymentRecordNotFoundException;
import io.coredge.health.claims.payments.exception.PaymentValidationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.file.AccessDeniedException;
import java.util.List;

import static io.coredge.health.claims.exception.models.ErrorCode.BUSINESS_EXCEPTION;
import static io.coredge.health.claims.exception.models.ErrorCode.SYSTEM_EXCEPTION;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@ControllerAdvice
@Slf4j
@AllArgsConstructor
public class GlobalExceptionHandler {

    private static final String EXCEPTION_MSG_FORMAT = "{} Execution occurred while processing the request. Exception : {}";
    private ExceptionHelper helper;

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException authExp, WebRequest request) {
        log.warn(EXCEPTION_MSG_FORMAT, "Authentication", authExp.getMessage());
        return new ResponseEntity<>(helper.apiError(authExp, request.getLocale()), UNAUTHORIZED);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException busExp, WebRequest request) {
        log.error(EXCEPTION_MSG_FORMAT, "Business", busExp.getMessage());
        return ResponseEntity.unprocessableEntity().body(helper.apiError(busExp, request.getLocale()));
    }

    @ExceptionHandler(NoLimitException.class)
    public ResponseEntity<String> handleNoLimitException(NoLimitException ex) {
        return ResponseEntity.ok().body(ex.getMessage());
    }

    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<String> handleInvalidDate(InvalidFormatException ex) {
        return ResponseEntity.badRequest().body(ex.getOriginalMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException valExp,
            WebRequest request) {
        return ResponseEntity.badRequest().body(helper.apiError(valExp, request.getLocale()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleValidationException(HttpMessageNotReadableException valExp,
            WebRequest request) {
        return ResponseEntity.badRequest().body(helper.apiError(valExp, request.getLocale()));
    }

    @ExceptionHandler(DataBadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequestException(DataBadRequestException valExp, WebRequest request) {
        return ResponseEntity.badRequest().body(helper.apiError(valExp, request.getLocale()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateKeyException(DuplicateKeyException valExp, WebRequest request) {
        return ResponseEntity.badRequest().body(helper.apiError(valExp, request.getLocale()));
    }

    @ExceptionHandler(DataNotFoundException.class)
    public ResponseEntity<ApiError> handleDataNotFoundException(DataNotFoundException valExp, WebRequest request) {
        log.error("Data not found: {}", valExp.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(helper.apiError(valExp, request.getLocale()));
    }

    @ExceptionHandler(DataAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleDataAlreadyExistsException(DataAlreadyExistsException ex,
            WebRequest request) {
        log.error("Data already exists: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(helper.apiError(ex, request.getLocale()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    @ExceptionHandler(MaxLimitExceededException.class)
    public ResponseEntity<ApiError> handleBatchLimitExceededException(MaxLimitExceededException ex,
            WebRequest request) {
        log.error("Batch limit exceeded: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(helper.apiError(ex, request.getLocale()));
    }

    @ExceptionHandler(FacilityLevelMismatchException.class)
    public ResponseEntity<String> handleMismatch(FacilityLevelMismatchException ex) {
        log.error("Facility level mismatch", ex);
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(FacilityLevelNotFoundException.class)
    public ResponseEntity<String> handleNotFound(FacilityLevelNotFoundException ex) {
        log.error("Facility level not found", ex);
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiError> handleWebClientResponseException(WebClientResponseException ex,
            WebRequest request) {
        log.error(EXCEPTION_MSG_FORMAT, "WebClientResponse", ex.getMessage());
        return ResponseEntity.status(ex.getRawStatusCode()).body(helper.apiError(ex, request.getLocale()));
    }

    /*
     * Related to SHR exception while fetching the IPS information
     * EPORTAL-397
     */
    @ExceptionHandler(ShrDataNotFoundException.class)
    public ResponseEntity<ApiError> handleShrNotFound(ShrDataNotFoundException ex) {
        ErrorDetails details = ErrorDetails.builder()
                .code("404")
                .message(ex.getMessage())
                .attribute(null)
                .build();

        ApiError error = ApiError.builder()
                .code(ErrorCode.CODE_DATA_NOT_FOUND.code())
                .message(ErrorCode.CODE_DATA_NOT_FOUND.description())
                .details(List.of(details))
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ShrServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleShrServiceUnavailableException(ShrServiceUnavailableException ex) {
        ErrorDetails details = ErrorDetails.builder()
                .code("503")
                .message(ex.getMessage())
                .attribute(null)
                .build();

        ApiError error = ApiError.builder()
                .code(ErrorCode.SHR_UNAVAILABLE.code()) // CLAIM-503
                .message(ErrorCode.SHR_UNAVAILABLE.description())
                .details(List.of(details))
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(ShrFetchException.class)
    public ResponseEntity<ApiError> handleShrFetchException(ShrFetchException ex) {
        ErrorDetails details = ErrorDetails.builder()
                .code("400")
                .message(ex.getMessage())
                .attribute(null)
                .build();

        ApiError error = ApiError.builder()
                .code(ErrorCode.BAD_REQUEST.code())
                .message(ErrorCode.BAD_REQUEST.description())
                .details(List.of(details))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(RuleViolationException.class)
    public ResponseEntity<ApiError> handleBusinessRuleViolation(RuleViolationException rve) {
        ErrorDetails details = ErrorDetails.builder()
                .code(BUSINESS_EXCEPTION.code())
                .message(rve.getMessage())
                .attribute(null)
                .build();

        ApiError error = ApiError.builder()
                .code(ErrorCode.BUSINESS_RULE_EXCEPTION.code()) // CLAIM-503
                .message(ErrorCode.BUSINESS_RULE_EXCEPTION.description())
                .details(List.of(details))
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(DataProcessingException.class)
    public ResponseEntity<ApiError> handleProcessingInProgress(DataProcessingException ex, WebRequest request) {
        log.info("202 Accepted – processing in progress: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(helper.apiError(ex, request.getLocale()));
    }

    @ExceptionHandler(PaymentRecordNotFoundException.class)
    public ResponseEntity<ApiError> handlePaymentRecordNotFound(PaymentRecordNotFoundException ex, WebRequest request) {
        log.error("Payment record not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(helper.apiError(ex, request.getLocale()));
    }

    @ExceptionHandler(PaymentDataMismatchException.class)
    public ResponseEntity<ApiError> handlePaymentDataMismatch(PaymentDataMismatchException ex, WebRequest request) {
        log.error("Payment data mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(helper.apiError(ex, request.getLocale()));
    }

    @ExceptionHandler(PaymentBatchMismatchException.class)
    public ResponseEntity<ApiError> handlePaymentBatchMismatch(PaymentBatchMismatchException ex, WebRequest request) {
        log.error("Payment batch mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(helper.apiError(ex, request.getLocale()));
    }

    @ExceptionHandler(PaymentInvalidStatusException.class)
    public ResponseEntity<ApiError> handlePaymentInvalidStatus(PaymentInvalidStatusException ex, WebRequest request) {
        log.error("Payment invalid status: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(helper.apiError(ex, request.getLocale()));
    }

    @ExceptionHandler(PaymentBatchInvalidStatusException.class)
    public ResponseEntity<ApiError> handlePaymentBatchInvalidStatus(PaymentBatchInvalidStatusException ex,
            WebRequest request) {
        log.error("Payment batch invalid status: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(helper.apiError(ex, request.getLocale()));
    }

    @ExceptionHandler(PaymentMissingDataException.class)
    public ResponseEntity<ApiError> handlePaymentMissingData(PaymentMissingDataException ex, WebRequest request) {
        log.error("Payment missing data: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(helper.apiError(ex, request.getLocale()));
    }

    @ExceptionHandler(PaymentValidationException.class)
    public ResponseEntity<ApiError> handlePaymentValidation(PaymentValidationException ex, WebRequest request) {
        log.error("Payment validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(helper.apiError(ex, request.getLocale()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleSystemException(Exception sysExp, WebRequest request) {
        log.error(EXCEPTION_MSG_FORMAT, "System", sysExp.getMessage(), sysExp);
        return new ResponseEntity<>(helper.apiError(SYSTEM_EXCEPTION, sysExp, request.getLocale()),
                INTERNAL_SERVER_ERROR);
    }

}
