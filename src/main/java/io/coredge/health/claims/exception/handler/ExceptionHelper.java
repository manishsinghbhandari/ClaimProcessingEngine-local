package io.coredge.health.claims.exception.handler;

import io.coredge.health.claims.exception.app.DataProcessingException;
import io.coredge.health.claims.exception.base.AuthenticationException;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.base.DataBadRequestException;
import io.coredge.health.claims.exception.callback.MaxLimitExceededException;
import io.coredge.health.claims.exception.models.ApiError;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;
import io.coredge.health.claims.exception.models.ErrorDetails;
import io.coredge.health.claims.exception.shr.ShrFetchException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.exception.models.ErrorCode.*;

@Component
@AllArgsConstructor
@Slf4j
public class ExceptionHelper {

    private static final String ERROR_KEY_FORMAT = "error.code.";
    private static final String CODE_DEFAULT_CODE = "$$";

    private MessageSource messageSource;

    public ApiError apiError(AuthenticationException exception, Locale locale) {
        ErrorDetails errorDetails = ErrorDetails.builder().code(exception.getCode().code()).message(getMessage(exception.getCode(), locale)).build();

        return ApiError.builder().code(UNAUTHORIZED.code()).message(getMessage(UNAUTHORIZED, locale)).details(Collections.singletonList(errorDetails)).build();
    }

    public ApiError apiError(BusinessException exception, Locale locale) {
        String message = getMessage(exception.getCode(), exception, locale);
        if (Objects.nonNull(exception.getAttribute()) && !StringUtils.isEmpty(exception.getAttribute().getKey()) && !StringUtils.isEmpty(exception.getAttribute().getValue())) {
            message = message.replaceAll("#" + exception.getAttribute().getKey(), exception.getAttribute().getValue());
        } else {
            if (StringUtils.isEmpty(message)) {
                message = exception.getMessage();
            }
        }
        ErrorDetails errorDetails = ErrorDetails.builder().code(exception.getCode().code()).message(message).attribute(exception.getAttribute()).build();

        return ApiError.builder().code(BUSINESS_EXCEPTION.code())
                .message(getMessage(BUSINESS_EXCEPTION, locale))
                .details(Collections.singletonList(errorDetails)).build();
    }

    public ApiError apiError(ErrorCode code, Locale locale) {
        return ApiError.builder().code(code.code()).message(getMessage(code, locale)).build();
    }

    public ApiError apiError(ErrorCode code, Exception exception, Locale locale) {
        List<ErrorDetails> errorDetails = populateErrorDetails(exception, locale);
        return ApiError.builder().code(code.code()).message(getMessage(code, locale)).details(errorDetails).build();
    }

    public ApiError apiError(MethodArgumentNotValidException exception, Locale locale) {
        List<ErrorDetails> errorDetails = populateErrorDetails(exception, locale);
        return ApiError.builder().code(BAD_REQUEST.code()).message(getMessage(BAD_REQUEST, locale)).details(errorDetails).build();
    }

    public ApiError apiError(DataBadRequestException exception, Locale locale) {
        List<ErrorDetails> errorDetails = populateErrorDetails(exception, locale);
        return ApiError.builder().code(BAD_REQUEST.code()).message(getMessage(BAD_REQUEST, locale)).details(errorDetails).build();
    }

    public ApiError apiError(DuplicateKeyException exception, Locale locale) {
        List<ErrorDetails> errorDetails = populateErrorDetails(exception, locale);
        return ApiError.builder().code(BAD_REQUEST.code()).message(getMessage(BAD_REQUEST, locale)).details(errorDetails).build();
    }

    public ApiError apiError(HttpMessageNotReadableException exception, Locale locale) {
        List<ErrorDetails> errorDetails = populateErrorDetails(exception, locale);
        return ApiError.builder().code(BAD_REQUEST.code()).message(getMessage(BAD_REQUEST, locale)).details(errorDetails).build();
    }


    public ApiError apiError(MaxLimitExceededException exception, Locale locale) {
        List<ErrorDetails> errorDetails = populateErrorDetails(exception, locale);
        return ApiError.builder().code(BAD_REQUEST.code()).message(getMessage(BAD_REQUEST, locale)).details(errorDetails).build();
    }

    public ApiError apiError(WebClientResponseException exception, Locale locale) {
        String responseBody = exception.getResponseBodyAsString();
        ErrorDetails detail = ErrorDetails.builder().code(THIRD_PARTY_API_ERROR.code()).message(responseBody != null ? responseBody : exception.getMessage()).build();
        return ApiError.builder().code(String.valueOf(exception.getRawStatusCode())).details(Collections.singletonList(detail)).build();
    }



    private String getActualErrorCode(ErrorCode code) {
        return Objects.nonNull(code) ? code.code() : "";
    }

    private ErrorCode getErrorCode(String code) {
        try {
            return ErrorCode.valueOf(code);
        } catch (Exception e) {
            log.error("No Error code found for {}.", code);
        }
        return null;
    }

    private String getMessage(ErrorCode code, Locale locale) {
        String message = "";
        if (Objects.nonNull(code)) {
            try {
                message = messageSource.getMessage(getMessageKey(code.code()), null, locale);
            } catch (NoSuchMessageException exp) {
                log.warn("No message found for {}. Falling back to enum description.", code.code());
                message = code.description();
            }
        }
        return message;
    }

    private String getMessage(ErrorCode code, BusinessException exception, Locale locale) {
        String message = "";
        if (Objects.nonNull(code)) {
            try {
                message = exception.getMessage();
                if (StringUtils.isNotBlank(message) && message.startsWith(CODE_DEFAULT_CODE)) {
                    message = StringUtils.substringAfter(message, CODE_DEFAULT_CODE);
                } else {
                    message = messageSource.getMessage(getMessageKey(code.code()), null, Locale.ENGLISH);
                }
            } catch (NoSuchMessageException exp) {
                log.error("No Message found for {}.", code.code());
            }
        }
        return message;
    }

    private String getMessageKey(String code) {
        return ERROR_KEY_FORMAT + code;
    }

    private String getValue(FieldError fieldError) {
        if (Objects.nonNull(fieldError.getRejectedValue())) {
            Object value = fieldError.getRejectedValue();
            return Objects.nonNull(value) ? value.toString() : null;
        }
        return null;
    }

    private ErrorDetails populateErrorDetail(FieldError fieldError, Locale locale) {
//        ErrorCode errorCode = getErrorCode(fieldError.getDefaultMessage());
        return ErrorDetails.builder().code(BAD_REQUEST.code()).message(fieldError.getDefaultMessage())   .attribute(ErrorAttribute.builder().key(fieldError.getField()).value(getValue(fieldError)).build()).build();
    }


    private List<ErrorDetails> populateErrorDetails(MethodArgumentNotValidException exception, Locale locale) {
        return exception.getBindingResult().getFieldErrors().stream().map(fieldError -> populateErrorDetail(fieldError, locale)).collect(Collectors.toList());
    }

    private List<ErrorDetails> populateErrorDetails(HttpMessageNotReadableException exception, Locale locale) {
        List<ErrorDetails> errorlist = new ArrayList<>();
        errorlist.add(ErrorDetails.builder().code("400").message(exception.getMessage()).build());
        return errorlist;
    }

    private List<ErrorDetails> populateErrorDetails(DataBadRequestException exception, Locale locale) {
        List<ErrorDetails> errorlist = new ArrayList<>();
        errorlist.add(ErrorDetails.builder().code("400").message(exception.getMessage()).build());
        return errorlist;
    }

    private List<ErrorDetails> populateErrorDetails(DuplicateKeyException exception, Locale locale) {
        List<ErrorDetails> errorlist = new ArrayList<>();
        errorlist.add(ErrorDetails.builder().code("400").message(exception.getMessage()).build());
        return errorlist;
    }

    private List<ErrorDetails> populateErrorDetails(Exception exception, Locale locale) {
        List<ErrorDetails> errorlist = new ArrayList<>();
        errorlist.add(ErrorDetails.builder().code("500").message(exception.getMessage()).build());
        return errorlist;
    }

    private List<ErrorDetails> populateErrorDetails(MaxLimitExceededException exception, Locale locale) {
        List<ErrorDetails> errorlist = new ArrayList<>();
        errorlist.add(ErrorDetails.builder().code("400").message(exception.getMessage()).build());
        return errorlist;
    }

    private List<ErrorDetails> populateErrorDetails(DataProcessingException exception, Locale locale) {
        List<ErrorDetails> errorList = new ArrayList<>();
        errorList.add(ErrorDetails.builder()
                .code(String.valueOf(HttpStatus.ACCEPTED.value()))
                .message(exception.getMessage())
                .attribute(null)
                .build());
        return errorList;
    }

    public ApiError apiError(DataProcessingException exception, Locale locale) {
        List<ErrorDetails> errorDetails = populateErrorDetails(exception, locale);
        return ApiError.builder()
                .code(ErrorCode.DATA_PROCESSING.code())  // "CLAIM-202"
                .message(getMessage(ErrorCode.DATA_PROCESSING, locale))
                .details(errorDetails)
                .build();
    }

}
