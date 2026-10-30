package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;

@ApiHttpContext
class CustomerRegistrationPasswordApiHttpIntegrationTest extends CustomerRegistrationPasswordScenarios {
    @Override protected String expectedContextPath() { return "/api"; }
}
