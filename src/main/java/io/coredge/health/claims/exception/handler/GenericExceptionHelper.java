package io.coredge.health.claims.exception.handler;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.base.DataBadRequestException;
import io.coredge.health.claims.exception.models.ApiError;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;
import io.coredge.health.claims.exception.models.ErrorDetails;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.coredge.health.claims.exception.models.ErrorCode.BAD_REQUEST;

@Component
@AllArgsConstructor
@Slf4j
public class GenericExceptionHelper {

    private static final String ERROR_KEY_FORMAT = "error.code.";
    private static final String CODE_DEFAULT_CODE = "$$";

    private MessageSource messageSource;

    public ApiError apiError(MethodArgumentNotValidException exception, Locale locale) {
        List<ErrorDetails> errorDetails = populateErrorDetails(exception, locale);
        return ApiError.builder().code(BAD_REQUEST.code()).message(getMessage(BAD_REQUEST, locale)).details(errorDetails).build();
    }

    private List<ErrorDetails> populateErrorDetails(MethodArgumentNotValidException exception, Locale locale) {
        return exception.getBindingResult().getFieldErrors().stream().map(fieldError -> populateErrorDetail(fieldError, locale)).collect(Collectors.toList());
    }

    private ErrorDetails populateErrorDetail(FieldError fieldError, Locale locale) {
        ErrorCode errorCode = getErrorCode(fieldError.getDefaultMessage());
        return ErrorDetails.builder().code(getActualErrorCode(errorCode)).message(getMessage(errorCode, locale)).attribute(ErrorAttribute.builder().key(fieldError.getField()).value(getValue(fieldError)).build()).build();
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
                log.error("No Message found for {}.", code.code());
            }
        }
        return message;
    }

    private String getMessageKey(String code) {
        return ERROR_KEY_FORMAT + code;
    }

    private String getValue(FieldError fieldError) {
        Object value = fieldError.getRejectedValue();
        return Objects.nonNull(value) ? fieldError.getRejectedValue().toString() : null;
    }

}
