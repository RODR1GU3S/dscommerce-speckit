package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Explicit test import only; requires the isolated database from AdminProductTestConfig. */
@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class OwnOrderQueryTestObservationConfig {

    @Bean
    public OwnOrderQueryDatabaseProbe ownOrderQueryDatabaseProbe(Environment environment) {
        return new OwnOrderQueryDatabaseProbe(environment.getRequiredProperty("spring.datasource.url"));
    }

    @Bean
    public SqlObservation ownOrderQuerySqlObservation() {
        return new SqlObservation();
    }

    @Bean
    public HibernatePropertiesCustomizer ownOrderQueryHibernateObservation(
            @Qualifier("ownOrderQuerySqlObservation") SqlObservation observation) {
        return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
    }
}
