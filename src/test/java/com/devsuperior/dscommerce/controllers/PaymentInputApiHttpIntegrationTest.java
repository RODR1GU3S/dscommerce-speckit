package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.ApiHttpContext;
import org.springframework.context.annotation.Import;

@ApiHttpContext
@Import(PaymentRegistrationObservationConfig.class)
class PaymentInputApiHttpIntegrationTest extends PaymentInputScenarios {
    @Override protected String expectedContextPath() { return "/api"; }
}
