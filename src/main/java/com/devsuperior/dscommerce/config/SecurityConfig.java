package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.security.AdminAccessDeniedHandler;
import com.devsuperior.dscommerce.security.AdminAuthenticationEntryPoint;
import com.devsuperior.dscommerce.security.OrderAccessDeniedHandler;
import com.devsuperior.dscommerce.security.OrderAuthenticationEntryPoint;
import com.devsuperior.dscommerce.security.OrderJwtAuthenticationConverter;
import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.time.Clock;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public OrderJwtAuthenticationConverter orderJwtAuthenticationConverter(AuthenticatedAccountService accounts,
                                                                          Clock clock) {
        return new OrderJwtAuthenticationConverter(accounts, clock);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain orderSecurityFilterChain(HttpSecurity http, JwtDecoder decoder,
                                                       OrderJwtAuthenticationConverter converter,
                                                       ObjectMapper mapper) throws Exception {
        var entryPoint = new OrderAuthenticationEntryPoint(mapper);
        var deniedHandler = new OrderAccessDeniedHandler(mapper);
        http
                .securityMatcher(new AntPathRequestMatcher("/orders", "POST"))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAnyRole("CLIENT", "ADMIN"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(token -> {
                            try {
                                return converter.convert(token);
                            } catch (OrderAccountResolutionException failure) {
                                // Keep the internal failure distinct while routing it to the filter entry point.
                                throw new OAuth2AuthenticationException(new OAuth2Error("server_error"),
                                        "Order registration could not be completed", failure);
                            }
                        })));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminProductSecurityFilterChain(HttpSecurity http, JwtDecoder decoder,
                                                              OrderJwtAuthenticationConverter converter,
                                                              ObjectMapper mapper) throws Exception {
        var entryPoint = new AdminAuthenticationEntryPoint(mapper);
        var deniedHandler = new AdminAccessDeniedHandler(mapper);
        http
                .securityMatcher(new AntPathRequestMatcher("/admin/**"))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(token -> {
                            try {
                                return converter.convert(token);
                            } catch (OrderAccountResolutionException failure) {
                                // Preserve internal lookup failures for the administrative entry point.
                                throw new OAuth2AuthenticationException(new OAuth2Error("server_error"),
                                        "Product maintenance could not be completed", failure);
                            }
                        })));
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll());

        return http.build();
    }
}
