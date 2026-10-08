package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.mockito.Mockito.mock;

/** MVC slices for public routes only; these mocks provide no JWT verification evidence. */
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "test.order-security.public-mvc-slice", havingValue = "true")
public class OrderSecuritySliceTestConfig {

    @Bean
    public JwtDecoder orderSliceJwtDecoder() { return mock(JwtDecoder.class); }

    @Bean
    public AuthenticatedAccountService orderSliceAccountService() { return mock(AuthenticatedAccountService.class); }
}
