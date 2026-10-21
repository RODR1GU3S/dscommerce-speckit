package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=/api", "spring.jpa.open-in-view=false",
                "spring.h2.console.enabled=false", "feature007.own-order-list-test-context=api"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(OwnOrderQueryTestObservationConfig.class)
class OwnOrderQueryListApiHttpIntegrationTest extends OwnOrderQueryListHttpTestSupport {
    @Override protected String expectedContextPath() { return "/api"; }
    @Override protected String expectedContextIdentity() { return "api"; }
}
