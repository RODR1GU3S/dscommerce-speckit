package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;

@ApiHttpContext
class CustomerRegistrationFlowApiHttpIntegrationTest extends CustomerRegistrationFlowScenarios {
    @Override protected String expectedContextPath() { return "/api"; }
}
