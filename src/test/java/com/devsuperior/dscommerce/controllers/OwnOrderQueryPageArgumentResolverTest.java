package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.OwnOrderQueryWebMvcConfig;
import com.devsuperior.dscommerce.dto.OwnOrderPageRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** MVC component probe only: no application security, service, database or HTTP server. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnOrderQueryPageArgumentResolverTest {

    private final OwnOrderPageArgumentResolver resolver = new OwnOrderPageArgumentResolver();
    private final ObjectMapper mapper = new ObjectMapper();
    private AnnotationConfigWebApplicationContext context;
    private Validator validator;
    private MvcFixtureController controller;
    private MockMvc mvc;
    private int dispatches;

    @BeforeAll
    void configureOnlyMvcWithTheProductionResolverRegistration() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles("feature007-t011-mvc-probe");
        context.register(MvcConfiguration.class);
        context.refresh();
        validator = context.getBean("mvcValidator", Validator.class);
        controller = context.getBean(MvcFixtureController.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        assertThat(context.containsBean("dataSource")).isFalse();
        assertThat(context.containsBean("ownOrderQueryAccessService")).isFalse();
        assertThat(context.containsBean("springSecurityFilterChain")).isFalse();
    }

    @BeforeEach
    void resetHandlerObservation() { controller.calls.set(0); }

    @AfterAll
    void closeOnlyThisMvcContext() {
        if (context != null) { context.close(); }
        System.out.println("T011_COUNTS mockMvcDispatches=" + dispatches + " realHttpRequests=0 databaseContexts=0");
    }

    @ParameterizedTest
    @MethodSource("validPages")
    void beanValidationAcceptsDefaultsBoundsAndLargestValidOffsets(PageCase page) {
        var input = input(page.page(), page.size());
        assertThat(validator.validate(input)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidTypedInputs")
    void beanValidationRejectsInvalidFieldsWithPureIndependentConstraints(Integer page, Integer size,
                                                                                  String expected) {
        var violations = validator.validate(input(page, size)).stream()
                .map(value -> value.getPropertyPath() + ":"
                        + value.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
                .collect(Collectors.toSet());
        assertThat(violations).containsExactlyInAnyOrderElementsOf(Set.of(expected.split(",")));
    }

    @ParameterizedTest
    @CsvSource({"valid,true", "unvalidated,false", "automatic,false", "legacy,false", "other,false"})
    void supportsOnlyTheDedicatedValidInputWithoutAutomaticModelAttribute(String method, boolean supported) {
        assertThat(resolver.supportsParameter(parameter(method))).isEqualTo(supported);
    }

    @ParameterizedTest
    @MethodSource("repeatedOrMissingOccurrences")
    void rejectsMultiplicityBeforeBinderCreationOrConversion(String name, String[] values) {
        var request = mock(NativeWebRequest.class);
        var factory = mock(WebDataBinderFactory.class);
        var original = new LinkedHashMap<String, String[]>();
        original.put(name, values);
        when(request.getParameterMap()).thenReturn(original);
        assertThatThrownBy(() -> resolver.resolveArgument(parameter("valid"), null, request, factory))
                .isInstanceOf(ServletRequestBindingException.class).hasMessage("Invalid order query data");
        verify(request, times(1)).getParameterMap();
        verifyNoMoreInteractions(request);
        verifyNoInteractions(factory);
    }

    @Test
    void filtersAndCopiesOriginalNamesBeforeBinderCreationAndNeverRereadsTheRequest() throws Exception {
        var request = mock(NativeWebRequest.class);
        var factory = mock(WebDataBinderFactory.class);
        var original = new LinkedHashMap<String, String[]>();
        original.put("page", new String[]{"1"});
        original.put("size", new String[]{"2"});
        original.put("_page", new String[]{"", "not-an-integer"});
        original.put("!page", new String[]{"3"});
        original.put("size[]", new String[]{"51"});
        original.put("buyerId", new String[]{"999"});
        when(request.getParameterMap()).thenReturn(original);
        var binds = new AtomicInteger();
        when(factory.createBinder(eq(request), any(OwnOrderPageRequestDTO.class), eq("ownOrderPageRequest")))
                .thenAnswer(invocation -> {
                    OwnOrderPageRequestDTO target = invocation.getArgument(1);
                    assertThat(target.getPage()).isZero();
                    assertThat(target.getSize()).isEqualTo(12);
                    // Prove values were copied before binder creation, not retained or fetched a second time.
                    var pageValues = original.get("page");
                    pageValues[0] = "999";
                    original.put("size", new String[]{"50"});
                    var binder = new WebDataBinder(target, "ownOrderPageRequest") {
                        @Override protected void doBind(MutablePropertyValues values) {
                            assertThat(values.getPropertyValueList()).extracting(value -> value.getName())
                                    .containsExactly("page", "size");
                            assertThat(values.getPropertyValue("page").getValue()).isNotSameAs(pageValues);
                            assertThat((String[]) values.getPropertyValue("page").getValue()).containsExactly("1");
                            assertThat((String[]) values.getPropertyValue("size").getValue()).containsExactly("2");
                            binds.incrementAndGet();
                            super.doBind(values);
                        }
                    };
                    binder.setValidator((org.springframework.validation.Validator) validator);
                    return binder;
                });
        var result = resolver.resolveArgument(parameter("valid"), null, request, factory);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(binds.get()).isEqualTo(1);
        verify(request, times(1)).getParameterMap();
        verifyNoMoreInteractions(request);
    }

    @Test
    void configurationRegistersTheResolverBeforeTheAutomaticModelAttributeFallback() {
        var resolvers = context.getBean(RequestMappingHandlerAdapter.class).getArgumentResolvers();
        var positions = java.util.stream.IntStream.range(0, resolvers.size())
                .filter(index -> resolvers.get(index) instanceof OwnOrderPageArgumentResolver).toArray();
        assertThat(positions).hasSize(1);
        assertThat(resolvers.getLast()).isInstanceOf(ModelAttributeMethodProcessor.class);
        assertThat(positions[0]).isLessThan(resolvers.size() - 1);
    }

    @ParameterizedTest
    @MethodSource("mvcValidPages")
    void mvcConvertsOnlyPresentParametersAndValidatesBeforeCallingTheHandler(String path, PageCase page)
            throws Exception {
        assertPage(path, page.query(), page.page(), page.size());
    }

    @ParameterizedTest
    @MethodSource("mvcInvalidQueries")
    void mvcRejectsInvalidExactQueriesBeforeCallingTheHandler(String path, String query) throws Exception {
        assertRejected(path, query);
    }

    @ParameterizedTest
    @MethodSource("mvcAliases")
    void mvcIgnoresEveryAliasVariantWithDefaultsAndExplicitValues(String path, String aliases, boolean explicit)
            throws Exception {
        assertPage(path, (explicit ? "page=1&size=2&" : "") + aliases, explicit ? 1 : 0, explicit ? 2 : 12);
    }

    @ParameterizedTest
    @MethodSource("mvcInvalidQueriesWithAliases")
    void mvcAliasesCannotRepairInvalidOrRepeatedExactParameters(String path, String query) throws Exception {
        assertRejected(path, query);
    }

    @Test
    void mvcIgnoresOwnerSelectorsSortCaseVariantsAndNestedPropertyNames() throws Exception {
        assertPage("", "accountId=1&buyerId=2&sort=id,asc&sort=invalid&Page=3&Size=1"
                + "&offsetWithinRange=false&page.value=3&size[0]=1&class.name=unavailable", 0, 12);
    }

    @ParameterizedTest
    @CsvSource({"ROOT", "API"})
    void mvcPreservesTheDefaultBinderForOtherInputTypes(String contextName) throws Exception {
        var path = contextName.equals("ROOT") ? "" : "/api";
        var response = mvc.perform(request(path, "/t011/legacy", "!page=3")).andReturn();
        dispatches++;
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(response.getResponse().getContentAsByteArray()).get("page").intValue())
                .isEqualTo(3); // Native default-prefix handling remains available outside this DTO.
        assertThat(controller.calls.get()).isEqualTo(1);
    }

    @Test
    void helperConstraintDoesNotBecomeAnInputOrJsonResponseField() throws Exception {
        var fields = new java.util.HashSet<String>();
        mapper.readTree(mapper.writeValueAsBytes(new OwnOrderPageRequestDTO())).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("page", "size");
    }

    private void assertPage(String path, String query, int page, int size) throws Exception {
        var response = mvc.perform(request(path, "/t011/page", query)).andReturn();
        dispatches++;
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(response.getResponse().getContentAsByteArray()))
                .isEqualTo(mapper.createObjectNode().put("page", page).put("size", size));
        assertThat(controller.calls.get()).isEqualTo(1);
    }

    private void assertRejected(String path, String query) throws Exception {
        var response = mvc.perform(request(path, "/t011/page", query)).andReturn();
        dispatches++;
        assertThat(response.getResponse().getStatus()).isEqualTo(400);
        assertThat(controller.calls.get()).isZero();
        assertThat(response.getResolvedException()).isNotNull();
    }

    private static MockHttpServletRequestBuilder request(String path, String route, String query) {
        var request = get(path + route).contextPath(path);
        if (!query.isEmpty()) {
            for (var pair : query.split("&")) {
                var parts = pair.split("=", 2);
                request.param(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
            }
        }
        return request;
    }

    private static OwnOrderPageRequestDTO input(Integer page, Integer size) {
        var input = new OwnOrderPageRequestDTO();
        input.setPage(page);
        input.setSize(size);
        return input;
    }

    private static MethodParameter parameter(String name) {
        return new MethodParameter(Arrays.stream(ParameterSignatures.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name)).findFirst().orElseThrow(), 0);
    }

    static Stream<PageCase> validPages() {
        return Stream.of(new PageCase("", 0, 12), new PageCase("size=2", 0, 2),
                new PageCase("page=1", 1, 12), new PageCase("size=1", 0, 1), new PageCase("size=50", 0, 50),
                new PageCase("page=2147483647&size=1", Integer.MAX_VALUE, 1),
                new PageCase("page=1073741823&size=2", 1073741823, 2),
                new PageCase("page=42949672&size=50", 42949672, 50), new PageCase("page=0&size=12", 0, 12));
    }

    static Stream<Arguments> invalidTypedInputs() {
        return Stream.of(Arguments.of(null, 12, "page:NotNull"), Arguments.of(0, null, "size:NotNull"),
                Arguments.of(-1, 12, "page:Min"), Arguments.of(0, 0, "size:Min"),
                Arguments.of(0, -1, "size:Min"), Arguments.of(0, 51, "size:Max"),
                Arguments.of(1073741824, 2, "offsetWithinRange:AssertTrue"),
                Arguments.of(42949673, 50, "offsetWithinRange:AssertTrue"),
                Arguments.of(Integer.MAX_VALUE, 12, "offsetWithinRange:AssertTrue"),
                Arguments.of(null, null, "page:NotNull,size:NotNull"));
    }

    static Stream<Arguments> repeatedOrMissingOccurrences() {
        return Stream.of("page", "size").flatMap(name -> Stream.of(new String[]{"0", "1"},
                new String[]{"0", "0"}, new String[]{"", "0"}, new String[]{"0", ""}, new String[0])
                .map(values -> Arguments.of(name, values)));
    }

    static Stream<Arguments> mvcValidPages() {
        return Stream.of("", "/api").flatMap(path -> validPages().map(page -> Arguments.of(path, page)));
    }

    static Stream<String> invalidQueries() {
        return Stream.of("page=-1", "page=not-an-integer", "page=1.5", "page=1e0", "page=",
                "page=2147483648", "page=-2147483649", "size=0", "size=-1", "size=51",
                "size=not-an-integer", "size=1.5", "size=1e0", "size=", "size=2147483648",
                "page=0&page=1", "page=0&page=0", "page=&page=0", "page=0&page=",
                "size=2&size=3", "size=2&size=2", "size=&size=2", "size=2&size=",
                "page=1073741824&size=2", "page=42949673&size=50", "page=2147483647", "page=%20", "size=%20");
    }

    static Stream<Arguments> mvcInvalidQueries() {
        return Stream.of("", "/api").flatMap(path -> invalidQueries().map(query -> Arguments.of(path, query)));
    }

    static Stream<Arguments> mvcAliases() {
        return Stream.of("", "/api").flatMap(path -> aliases(false).stream()
                .flatMap(alias -> Stream.of(false, true).map(explicit -> Arguments.of(path, alias, explicit))));
    }

    static Stream<Arguments> mvcInvalidQueriesWithAliases() {
        return Stream.of("", "/api").flatMap(path -> Stream.of("page=-1&size=2", "page=0&page=1&size=2",
                        "page=0&size=", "page=0&size=2&size=2")
                .flatMap(invalid -> aliases(true).stream().map(alias -> Arguments.of(path, invalid + "&" + alias))));
    }

    private static List<String> aliases(boolean combinedOnly) {
        var cases = new ArrayList<String>();
        for (var variant : List.of("integer", "empty", "text", "fraction", "repeated")) {
            var combined = new ArrayList<String>();
            for (var alias : List.of("_page", "_size", "size[]", "!page")) {
                var name = URLEncoder.encode(alias, StandardCharsets.UTF_8);
                var query = switch (variant) {
                    case "integer" -> name + "=3";
                    case "empty" -> name + "=";
                    case "text" -> name + "=not-an-integer";
                    case "fraction" -> name + "=1.5";
                    case "repeated" -> name + "=&" + name + "=not-an-integer&" + name + "=3";
                    default -> throw new IllegalStateException("Unknown variant");
                };
                if (!combinedOnly) { cases.add(query); }
                combined.add(query);
            }
            cases.add(String.join("&", combined));
        }
        return cases;
    }

    record PageCase(String query, int page, int size) { }

    static class ParameterSignatures {
        void valid(@Valid OwnOrderPageRequestDTO input) { }
        void unvalidated(OwnOrderPageRequestDTO input) { }
        void automatic(@Valid @ModelAttribute OwnOrderPageRequestDTO input) { }
        void legacy(@Valid LegacyPage input) { }
        void other(@Valid String input) { }
    }

    public static class LegacyPage {
        private int page;
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(OwnOrderQueryWebMvcConfig.class)
    static class MvcConfiguration {
        @Bean MvcFixtureController mvcFixtureController() { return new MvcFixtureController(); }
    }

    /** Unique profile also prevents component scanning into other full application tests. */
    @RestController
    @Profile("feature007-t011-mvc-probe")
    public static class MvcFixtureController {
        private final AtomicInteger calls = new AtomicInteger();
        @GetMapping("/t011/page")
        public Map<String, Integer> page(@Valid OwnOrderPageRequestDTO input) {
            calls.incrementAndGet();
            return Map.of("page", input.getPage(), "size", input.getSize());
        }
        @GetMapping("/t011/legacy")
        public Map<String, Integer> legacy(@ModelAttribute LegacyPage input) {
            calls.incrementAndGet();
            return Map.of("page", input.getPage());
        }
    }
}
