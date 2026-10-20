package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryFailureInjectionTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=/api", "spring.jpa.open-in-view=false", "spring.h2.console.enabled=false",
                "feature007.own-order-failure-test-context=api"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import({OwnOrderQueryTestObservationConfig.class, OwnOrderQueryFailureInjectionTestConfig.class})
class OwnOrderQueryFailureApiHttpIntegrationTest extends OwnOrderQueryFailureHttpTestSupport {
    @Override protected String contextPath() { return "/api"; }
    @Override protected String identity() { return "api"; }
}
