package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;

@ApiHttpContext
class CustomerRegistrationDuplicateApiHttpIntegrationTest extends CustomerRegistrationDuplicateScenarios {
    @Override protected String expectedContextPath() { return "/api"; }
}
