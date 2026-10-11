package com.devsuperior.dscommerce.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.ServletContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class H2ConsoleSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @LocalServerPort private int port;
    @Autowired private ServletContext servletContext;
    @Autowired private FilterChainProxy filters;

    @Test
    void shouldAllowSameOriginFramingForEnabledH2Console() throws Exception {
        assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class))
                .isTrue();

        mockMvc.perform(get("/h2-console/"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    void shouldRegisterTheConfiguredConsoleInTheRealPublicSecurityChain() {
        assertConsoleConfiguration(environment, servletContext, filters, "");
    }

    @ParameterizedTest(name = "real H2 {0}, invalid Bearer={1}")
    @MethodSource("consoleRequests")
    void shouldServeTheRealConsoleAndItsResourcesWithSameOriginFrames(
            String resource, boolean invalidBearer) throws Exception {
        assertConsoleAccess(port, "", resource, invalidBearer);
    }

    static Stream<Arguments> consoleRequests() {
        return Stream.of(false, true).flatMap(bearer -> Stream.of("", "login.jsp", "stylesheet.css")
                .map(resource -> Arguments.of(resource, bearer)));
    }

    private static void assertConsoleConfiguration(Environment environment, ServletContext context,
                                                    FilterChainProxy filters, String contextPath) {
        assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.h2.console.path")).isEqualTo("/h2-console");
        assertThat(context.getContextPath()).isEqualTo(contextPath);
        var console = context.getServletRegistrations().values().stream()
                .filter(registration -> registration.getClassName().equals("org.h2.server.web.JakartaWebServlet"))
                .toList();
        assertThat(console).hasSize(1);
        assertThat(console.getFirst().getMappings()).containsExactly("/h2-console/*");

        var request = new MockHttpServletRequest("GET", contextPath + "/h2-console/");
        request.setContextPath(contextPath);
        request.setServletPath("/h2-console");
        request.setPathInfo("/");
        var matching = filters.getFilterChains().stream().filter(chain -> chain.matches(request)).findFirst()
                .orElseThrow();
        assertThat(matching.getFilters()).anyMatch(AuthorizationFilter.class::isInstance)
                .noneMatch(BearerTokenAuthenticationFilter.class::isInstance)
                .noneMatch(CsrfFilter.class::isInstance);
    }

    private static void assertConsoleAccess(int port, String contextPath, String resource,
                                            boolean invalidBearer) throws Exception {
        // MockMvc does not dispatch the H2 servlet. Use the embedded server and its real filters.
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10)).build()) {
            var uri = URI.create("http://localhost:" + port + contextPath + "/h2-console/" + resource);
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10));
            if (invalidBearer) request.header("Authorization", "Bearer not-a-valid-jwt");
            var response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.uri().getPath()).isEqualTo(contextPath + "/h2-console/" + resource);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().allValues("X-Frame-Options")).containsExactly("SAMEORIGIN");
            assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
            assertThat(response.headers().allValues("Location")).isEmpty();
            if (resource.endsWith(".css")) {
                assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/css");
                assertThat(response.body()).isNotBlank();
            } else {
                assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/html");
                assertThat(response.body()).containsIgnoringCase("<html");
            }
        }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "server.servlet.context-path=/api")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class ServletContextPath {
        private static final String DATABASE_URL = "jdbc:h2:mem:h2-console-context-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

        @LocalServerPort private int contextPort;
        @Autowired private Environment contextEnvironment;
        @Autowired private ServletContext contextServlet;
        @Autowired private FilterChainProxy contextFilters;

        @DynamicPropertySource
        static void isolatedDatabase(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", () -> DATABASE_URL);
        }

        @Test
        void shouldKeepTheConsoleMappingRelativeToTheServletContext() {
            assertConsoleConfiguration(contextEnvironment, contextServlet, contextFilters, "/api");
        }

        @ParameterizedTest(name = "real /api H2 {0}, invalid Bearer={1}")
        @MethodSource("com.devsuperior.dscommerce.config.H2ConsoleSecurityIntegrationTest#consoleRequests")
        void shouldServeTheRealConsoleUnderTheExternalContextPath(String resource, boolean invalidBearer)
                throws Exception {
            assertConsoleAccess(contextPort, "/api", resource, invalidBearer);
        }
    }
}
