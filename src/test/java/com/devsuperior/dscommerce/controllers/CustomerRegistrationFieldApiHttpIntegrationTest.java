package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;

@ApiHttpContext
class CustomerRegistrationFieldApiHttpIntegrationTest extends CustomerRegistrationFieldScenarios {
    @Override protected String expectedContextPath() { return "/api"; }
}
