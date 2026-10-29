package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;
import org.springframework.context.annotation.Import;

@ApiHttpContext
@Import({CustomerRegistrationObservationConfig.class, CustomerRegistrationFailureInjectionConfig.class,
        CustomerRegistrationEffectsScenarios.EffectsDeliveryConfig.class})
class CustomerRegistrationEffectsApiHttpIntegrationTest extends CustomerRegistrationEffectsScenarios {
    @Override protected String expectedContextPath() { return "/api"; }
}
