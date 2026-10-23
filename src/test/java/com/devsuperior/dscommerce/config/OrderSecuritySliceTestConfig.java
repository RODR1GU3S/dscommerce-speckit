package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import com.devsuperior.dscommerce.services.OwnOrderQueryAccessService;
import com.devsuperior.dscommerce.services.PaymentAccessService;
import com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;

/** MVC slices for public routes only; these mocks provide no JWT verification evidence. */
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "test.order-security.public-mvc-slice", havingValue = "true")
public class OrderSecuritySliceTestConfig {

    @Bean
    public JwtDecoder orderSliceJwtDecoder() { return mock(JwtDecoder.class); }

    @Bean
    public AuthenticatedAccountService orderSliceAccountService() { return mock(AuthenticatedAccountService.class); }

    @Bean
    public OwnOrderQueryAccessService orderSliceQueryAccessService() { return mock(OwnOrderQueryAccessService.class); }

    @Bean
    public PaymentAccessService orderSlicePaymentAccessService() {
        var access = mock(PaymentAccessService.class);
        // Public-route slices do not exercise payment permission: never grant it by a void mock default.
        doThrow(new PaymentRegistrationException(PaymentRegistrationException.Reason.FORBIDDEN))
                .when(access).requireAdmin(any());
        return access;
    }
}
