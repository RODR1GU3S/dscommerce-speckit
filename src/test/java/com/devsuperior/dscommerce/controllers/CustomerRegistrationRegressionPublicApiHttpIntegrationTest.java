package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;

@ApiHttpContext
class CustomerRegistrationRegressionPublicApiHttpIntegrationTest extends CustomerRegistrationRegressionPublicScenarios {
    @Override protected String expectedContextPath() { return "/api"; }
}
