package com.example.lowcode.auth.security;

import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.common.web.SecurityErrorResponseWriter;
import com.example.lowcode.common.web.TraceIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        TraceIdFilter traceIdFilter,
        SecurityErrorResponseWriter securityErrorResponseWriter,
        CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, exception) ->
            securityErrorResponseWriter.write(request, response, ErrorCode.UNAUTHORIZED);
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
            securityErrorResponseWriter.write(request, response, ErrorCode.FORBIDDEN);

        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/home").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/template-categories").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/templates/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/template-cover-assets/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .addFilterBefore(traceIdFilter, BearerTokenAuthenticationFilter.class)
            .build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtTokenService jwtTokenService) {
        return NimbusJwtDecoder.withSecretKey(jwtTokenService.signingKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${app.web.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String tenantRole = jwt.getClaimAsString("tenantRole");
            if (tenantRole == null || tenantRole.isBlank()) {
                return List.of();
            }
            return List.of(new SimpleGrantedAuthority("ROLE_" + tenantRole));
        });
        return converter;
    }
}
