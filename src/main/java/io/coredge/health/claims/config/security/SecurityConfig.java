package io.coredge.health.claims.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.SecurityConstant.*;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${allow.all.request:false}") // Default to false if not set
    private boolean allowAllRequests;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        if (allowAllRequests) {
            // Allow all requests unconditionally if the flag is enabled
            httpSecurity
                    .authorizeHttpRequests(registry -> registry.anyRequest().permitAll())
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        } else {
            httpSecurity
                    .authorizeHttpRequests(registry -> registry
                            // Permit access to Swagger and other bypassed endpoints
                            .requestMatchers(SECURITY_BYPASS_URL_PATTERN).permitAll()
                            // Restrict API endpoints to users with specific roles
                            .requestMatchers(BASE_URL_PATTERN).hasAnyAuthority(ALLOWED_ROLES.toArray(new String[0]))
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2Configurer ->
                            oauth2Configurer.jwt(jwtConfigurer ->
                                    jwtConfigurer.jwtAuthenticationConverter(jwt -> {
                                        // Extract resource access from JWT token
                                        Map<String, Map<String, Collection<String>>> resourceAccess = jwt.getClaim("resource_access");
                                        Collection<String> roles = new HashSet<>();

                                        if (resourceAccess != null) {
                                            log.debug("Full Resource Access: {}", resourceAccess); // Debug log for full structure
                                            resourceAccess.forEach((client, value) -> {
                                                if (value.containsKey("roles")) {
                                                    roles.addAll(value.get("roles"));
                                                }
                                            });
                                        }

                                        // Debugging log to verify extracted roles
                                        log.debug("Extracted Roles: {}", roles);

                                        // Filter and map only the allowed roles into granted authorities
                                        List<GrantedAuthority> grantedAuthorities = roles.stream()
                                                .filter(ALLOWED_ROLES::contains)
                                                .map(SimpleGrantedAuthority::new)
                                                .collect(Collectors.toList());

                                        // Deny access if no allowed roles are found
                                        if (grantedAuthorities.isEmpty()) {
                                            log.error("Access Denied: User does not have required roles");
                                            throw new AccessDeniedException("User does not have the required roles to access this resource");
                                        }
                                        return new JwtAuthenticationToken(jwt, grantedAuthorities);
                                    })));

            // Enforce stateless session and disable CSRF protection since JWT is used
            httpSecurity.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .csrf(AbstractHttpConfigurer::disable);
        }
        return httpSecurity.build();
    }
}