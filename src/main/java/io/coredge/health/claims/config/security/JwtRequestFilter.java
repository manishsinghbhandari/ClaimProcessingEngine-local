package io.coredge.health.claims.config.security;

import static io.coredge.health.claims.exception.models.ErrorCode.*;

import com.google.gson.Gson;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.UserConsumer;
import io.coredge.health.claims.exception.app.ResourceNotAuthorizedException;
import io.coredge.health.claims.exception.app.UserNotAuthorizedException;
import io.coredge.health.claims.exception.models.ApiError;
import io.coredge.health.claims.exception.models.ErrorDetails;
import io.coredge.health.claims.service.UserConsumerService;
import io.coredge.health.claims.utils.DateUtils;
import io.coredge.health.claims.utils.JwtDecoderUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

@Slf4j
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Value("${allow.all.request:false}") // Default to false if not set
    private boolean allowAllRequests;

    @Value("${preauth.expiry.days:7}")
    private String preauthExpiryDays;

    private final UserConsumerService userConsumerService;

    private static final String USER_NOT_AUTHORIZED = "User is not authorized";

    public JwtRequestFilter(UserConsumerService userConsumerService) {
        this.userConsumerService = userConsumerService;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                                    @NotNull FilterChain chain) throws ServletException, IOException {
        try {
            if (ByUrlSecurityCheck(request, response, chain))
                return;
            // Get JWT token from HTTP request
            // Check if the Authorization header is present and starts with "Bearer "
            String token = getTokenFromRequest(request);
            String username;
            if (StringUtils.hasText(token)) {
                username = extractUsernameFromToken(token); // Implement this method
                if (StringUtils.hasText(username)) {
                    log.info("User [{}] logged in.", username);
                    HealthIdContextHolder.userId(username);
                    // Handle the External Consumer, Validate the Token
                }
                // Set expiry date here
                LocalDate expiryDate = DateUtils.nowNairobiLocalDate().plusDays(Integer.parseInt(preauthExpiryDays));
                HealthIdContextHolder.preAuthExpiryDate(expiryDate);
                // checking whether current user having admin role or not
                HealthIdContextHolder.hasAdminRole(JwtDecoderUtils.hasAdminRole(token));
                // pushing entire token in holder
                HealthIdContextHolder.jwtToken(token);
            } else {
                // Validate the ExternalConsumer token/apikey
                String apikey = getApiKey(request);
                String requestSource = getRequestSource(request);
                if (StringUtils.hasText(requestSource) && StringUtils.hasText(apikey)) {
                    // External Consumer. Valid by DB
                    Boolean isValid = validateExternalConsumer(request);
                    if (isValid) {
                        log.info("User [{}] External Source logged in.", requestSource);
                        HealthIdContextHolder.requestSource(requestSource);
                        HealthIdContextHolder.userId(requestSource);
                    }
                    // Set expiry date here
                    LocalDate expiryDate = LocalDate.now().plusDays(Integer.parseInt(preauthExpiryDays));
                    HealthIdContextHolder.preAuthExpiryDate(expiryDate);
                } else if (StringUtils.hasText(apikey)) {
                    // Set expiry date here
                    LocalDate expiryDate = LocalDate.now().plusDays(Integer.parseInt(preauthExpiryDays));
                    HealthIdContextHolder.preAuthExpiryDate(expiryDate);
                    backwardCompatibility(request); // added for the backward security compliance
                } else {
                    throw new ResourceNotAuthorizedException(USER_NOT_AUTHORIZED);
                }
            }
            chain.doFilter(request, response);
        } catch (ResourceNotAuthorizedException | UserNotAuthorizedException e) {
            // Handle custom exception
            log.error("Authorization failed: {}", e.getMessage());
            ErrorDetails errorDetails = ErrorDetails.builder().code(e.getCode().code()).message(e.getMessage()).build();
            ApiError error = ApiError.builder().code(UNAUTHORIZED.code()).message(HttpStatus.UNAUTHORIZED.name())
                    .details(Collections.singletonList(errorDetails)).build();
            // Write the custom error response to the response output stream
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(new Gson().toJson(error));
        }
    }

    private boolean ByUrlSecurityCheck(@NotNull HttpServletRequest req, @NotNull HttpServletResponse res,
                                       @NotNull FilterChain chain) throws IOException, ServletException {
        String uri = req.getRequestURI();
        if (allowAllRequests || Set
                .of("/swagger-ui/", "/swagger-resources/", "/v3/api-docs", "/admin/auth/generateToken",
                        "/claimEngine/actuator/prometheus", "/claimEngine/actuator/health/readiness",
                        "/claimEngine/actuator/health/liveness")
                .stream().anyMatch(path -> uri.startsWith(contextPath + path))) {
            // Set expiry date here
            LocalDate expiryDate = DateUtils.nowNairobiLocalDate().plusDays(Integer.parseInt(preauthExpiryDays));
            HealthIdContextHolder.preAuthExpiryDate(expiryDate);
            chain.doFilter(req, res);
            return true;
        }
        return false;
    }

    // Extract the token
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(Constants.Request.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(Constants.Request.BEARER)) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String getRequestSource(HttpServletRequest request) {
        return request.getHeader(Constants.Request.X_REQUEST_SOURCE);
    }

    private String getApiKey(HttpServletRequest request) {
        String apiKey = request.getHeader(Constants.Request.API_KEY.trim());
        if (!StringUtils.hasText(apiKey)) {
            apiKey = request.getHeader(Constants.Request.X_API_KEY);
        }
        return apiKey;
    }

    private String extractUsernameFromToken(String token) {
        // Implement JWT parsing logic here to extract the username
        return JwtDecoderUtils.getUserIdByToken(token);
    }

    private Boolean validateExternalConsumer(HttpServletRequest request) {
        // Validate the ExternalConsumer token/apikey
        String apikey = getApiKey(request);
        String requestSource = getRequestSource(request);
        if (!StringUtils.hasText(requestSource) || !StringUtils.hasText(apikey)) {
            throw new ResourceNotAuthorizedException("User is not authorized");
        }
        if (StringUtils.hasText(apikey) && StringUtils.hasText(requestSource)) {
            boolean isValidLogin = userConsumerService.validateUser(requestSource.trim(), apikey.trim());
            if (isValidLogin) {
                // Added consumer in the source for better readability. Some users are assigned
                // random alphanumeric values.So using consumer instead of userID.
                UserConsumer userConsumer = userConsumerService.getUserData(requestSource.trim());
                HealthIdContextHolder.requestSource(userConsumer.getConsumer());
                HealthIdContextHolder.userId(userConsumer.getConsumer());
            }
            return isValidLogin;
        }

        return Boolean.FALSE;
    }

    private void backwardCompatibility(HttpServletRequest request) {
        // Added the backward Security Compatibility
        String apikey = getApiKey(request);
        UserConsumer userConsumer = userConsumerService.fetchUserViaToken(apikey);
        if (userConsumer != null) {
            HealthIdContextHolder.userId(userConsumer.getConsumer());
            HealthIdContextHolder.requestSource(userConsumer.getConsumer());
        }
    }

}
