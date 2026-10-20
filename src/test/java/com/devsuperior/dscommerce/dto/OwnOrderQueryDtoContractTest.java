package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.entities.OrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Summary representation only: Boot's JSON mapper, without MVC, security or persistence. */
@JsonTest
class OwnOrderQueryDtoContractTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-08T15:00:00.123Z");

    @Autowired private ObjectMapper mapper;
    @Autowired private ApplicationContext context;

    @BeforeEach
    void keepThisProbeLimitedToJson() {
        assertThat(context.containsBean("dataSource")).isFalse();
        assertThat(context.containsBean("requestMappingHandlerAdapter")).isFalse();
        assertThat(context.containsBean("springSecurityFilterChain")).isFalse();
    }

    @Test
    void summarySerializesExactlyFourFieldsWithIntegralIdAndStoredStatus() throws Exception {
        var json = json(summary(Long.MAX_VALUE, "42.50"));
        assertSummary(json, Long.MAX_VALUE, "42.50");
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-08T15:00:00.123Z,2026-10-08T15:00:00.123Z",
            "2026-10-08T15:00:00Z,2026-10-08T15:00:00.000Z",
            "2026-10-08T18:00:00.123+03:00,2026-10-08T15:00:00.123Z",
            "2026-10-08T15:00:00.123456789Z,2026-10-08T15:00:00.123Z"})
    void summaryFormatsUtcMillisecondsWithoutChangingTheSuppliedInstant(String timestamp, String expected)
            throws Exception {
        var instant = Instant.parse(timestamp);
        var summary = new OwnOrderSummaryDTO(1L, instant, OrderStatus.WAITING_PAYMENT, "1.00");
        var value = json(summary).get("createdAt");
        assertThat(value.isTextual()).isTrue();
        assertThat(value.textValue()).isEqualTo(expected);
        assertThat(summary.createdAt()).isEqualTo(instant);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "0.10", "0.99", "1.00", "42.50", "10000000.00",
            "9007199254740993.01", "99999999999999999.98", "99999999999999999.99"})
    void summaryPreservesExactMoneyTextFromOneCentToDecimalCapacity(String total) throws Exception {
        var summary = summary(1L, total);
        assertThat(summary.total()).isEqualTo(total);
        assertSummary(json(summary), 1L, total);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0.00", "-1.00", "1", "1.0", "1.000", "1.001", "1e2", "1E+2", "01.00",
            "1,00", "1,000.00", "100000000000000000.00", " 1.00", "1.00 ", "+1.00", "NaN", "Infinity"})
    void summaryRejectsNonContractMoneyWithoutRoundingOrReformatting(String total) {
        assertThatThrownBy(() -> summary(1L, total)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void summaryFitsTheUnchangedPageContractWithoutAddingBuyerOrItemData() throws Exception {
        var page = new PageResponseDTO<>(List.of(summary(9L, "42.50"), summary(3L, "0.01")), 1, 2, 5L, 3);
        var json = json(page);
        assertPage(json, 1, 2, 5L, 3);
        assertThat(json.get("content").isArray()).isTrue();
        assertThat(json.get("content").size()).isEqualTo(2);
        // These are supplied DTO values, not a repository ordering/count calculation.
        assertSummary(json.get("content").get(0), 9L, "42.50");
        assertSummary(json.get("content").get(1), 3L, "0.01");
    }

    @ParameterizedTest
    @CsvSource({"0,12,0,0", "3,2,5,3"})
    void summaryPageSerializesSuppliedEmptyContentAndMetadata(int page, int size, long total, int pages)
            throws Exception {
        var response = new PageResponseDTO<OwnOrderSummaryDTO>(List.of(), page, size, total, pages);
        var json = json(response);
        assertPage(json, page, size, total, pages);
        assertThat(json.get("content").isArray()).isTrue();
        assertThat(json.get("content").isEmpty()).isTrue();
    }

    @Test
    void summaryRoundTripPreservesExactScalarValuesAtMillisecondPrecision() throws Exception {
        var original = summary(Long.MAX_VALUE, "9007199254740993.01");
        var bytes = mapper.writeValueAsBytes(original);
        var restored = mapper.readValue(bytes, OwnOrderSummaryDTO.class);
        assertThat(restored).isEqualTo(original);
        assertSummary(mapper.readTree(bytes), Long.MAX_VALUE, "9007199254740993.01");
    }

    private static OwnOrderSummaryDTO summary(long id, String total) {
        return new OwnOrderSummaryDTO(id, CREATED_AT, OrderStatus.WAITING_PAYMENT, total);
    }

    private JsonNode json(Object value) throws Exception { return mapper.readTree(mapper.writeValueAsBytes(value)); }

    private static void assertSummary(JsonNode json, long id, String total) {
        assertFields(json, "id", "createdAt", "status", "total");
        assertInteger(json.get("id"), id);
        assertThat(json.get("createdAt").isTextual()).isTrue();
        assertThat(json.get("createdAt").textValue()).isEqualTo("2026-10-08T15:00:00.123Z");
        assertThat(json.get("status").isTextual()).isTrue();
        assertThat(json.get("status").textValue()).isEqualTo("WAITING_PAYMENT");
        assertThat(json.get("total").isTextual()).isTrue();
        assertThat(json.get("total").textValue()).isEqualTo(total);
    }

    private static void assertPage(JsonNode json, int page, int size, long total, int pages) {
        assertFields(json, "content", "page", "size", "totalElements", "totalPages");
        assertInteger(json.get("page"), page);
        assertInteger(json.get("size"), size);
        assertInteger(json.get("totalElements"), total);
        assertInteger(json.get("totalPages"), pages);
    }

    private static void assertInteger(JsonNode value, long expected) {
        assertThat(value.isIntegralNumber()).isTrue();
        assertThat(value.canConvertToLong()).isTrue();
        assertThat(value.longValue()).isEqualTo(expected);
    }

    private static void assertFields(JsonNode object, String... expected) {
        assertThat(object.isObject()).isTrue();
        var fields = new HashSet<String>();
        object.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(expected);
    }
}
