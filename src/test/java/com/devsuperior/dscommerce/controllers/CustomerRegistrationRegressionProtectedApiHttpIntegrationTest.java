package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;

@ApiHttpContext
class CustomerRegistrationRegressionProtectedApiHttpIntegrationTest extends CustomerRegistrationRegressionProtectedScenarios {
    @Override protected String expectedContextPath() { return "/api"; }
}
