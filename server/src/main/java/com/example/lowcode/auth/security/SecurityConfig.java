package com.example.lowcode.auth.security;

import com.example.lowcode.common.exception.ErrorCode;
import com.example.lowcode.common.web.SecurityErrorResponseWriter;
import com.example.lowcode.common.web.TraceIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        TraceIdFilter traceIdFilter,
        SecurityErrorResponseWriter securityErrorResponseWriter
    ) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, exception) ->
            securityErrorResponseWriter.write(request, response, ErrorCode.UNAUTHORIZED);
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
            securityErrorResponseWriter.write(request, response, ErrorCode.FORBIDDEN);

        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/templates/**").permitAll()
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
                .jwt(Customizer.withDefaults())
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
}
