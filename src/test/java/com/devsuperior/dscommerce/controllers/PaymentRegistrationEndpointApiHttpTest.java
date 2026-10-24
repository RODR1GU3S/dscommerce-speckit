package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.ApiHttpContext;

@ApiHttpContext
class PaymentRegistrationEndpointApiHttpTest extends PaymentEndpointScenarios {
    @Override protected String expectedContextPath() { return "/api"; }
}
