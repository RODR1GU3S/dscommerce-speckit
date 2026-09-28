package com.devsuperior.dscommerce.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class H2ConsoleSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void shouldAllowSameOriginFramingForEnabledH2Console() throws Exception {
        assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class))
                .isTrue();

        mockMvc.perform(get("/h2-console/"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }
}
