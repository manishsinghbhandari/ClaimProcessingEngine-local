package io.coredge.health.claims.exception.handler;

import io.coredge.health.claims.exception.models.ApiError;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice(basePackages = "io.coredge.health.claims.rest.bms")
@Slf4j
@AllArgsConstructor
public class GenericModuleExceptionHandler {

    GenericExceptionHelper helper;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException valExp, WebRequest request) {
        return ResponseEntity.badRequest().body(helper.apiError(valExp, request.getLocale()));
    }




}
