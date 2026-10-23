package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.ApiHttpContext;
import org.springframework.context.annotation.Import;

/** Executes the same T024 assertions and parameter matrices under the real /api servlet context. */
@ApiHttpContext
@Import({PaymentFailureInjectionTestConfig.class, PaymentRollbackScenarios.FailureStagesConfig.class})
class PaymentRollbackApiHttpIntegrationTest extends PaymentRollbackScenarios {
    @Override protected String expectedContextPath() { return "/api"; }
}
