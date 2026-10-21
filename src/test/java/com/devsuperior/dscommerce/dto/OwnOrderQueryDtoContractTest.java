package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.entities.OrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Own-order representations only: Boot's JSON mapper, without MVC, security or persistence. */
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

    @Test
    void detailSerializesExactlyFiveFieldsAndRecordedBASequence() throws Exception {
        var b = new OwnOrderItemDTO(8L, "Produto B atual", 2, "10.00", "20.00");
        var a = new OwnOrderItemDTO(3L, " Produto A atual ", 3, "7.50", "22.50");
        var detail = new OwnOrderDetailDTO(Long.MAX_VALUE, CREATED_AT, OrderStatus.WAITING_PAYMENT,
                "42.50", List.of(b, a));
        var value = json(detail);
        assertDetailHeader(value, Long.MAX_VALUE, "42.50");
        assertThat(value.get("items").isArray()).isTrue();
        assertThat(value.get("items").size()).isEqualTo(2);
        assertItem(value.get("items").get(0), 8L, "Produto B atual", 2, "10.00", "20.00");
        assertItem(value.get("items").get(1), 3L, " Produto A atual ", 3, "7.50", "22.50");
    }

    @Test
    void detailItemSerializesOnlyFiveFieldsWithIntegralIdentifiersAndQuantity() throws Exception {
        var item = new OwnOrderItemDTO(Long.MAX_VALUE, "Produto atual", Integer.MAX_VALUE,
                "0.01", "21474836.47");
        assertItem(json(item), Long.MAX_VALUE, "Produto atual", Integer.MAX_VALUE, "0.01", "21474836.47");
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-08T15:00:00.123Z,2026-10-08T15:00:00.123Z",
            "2026-10-08T15:00:00Z,2026-10-08T15:00:00.000Z",
            "2026-10-08T18:00:00.123+03:00,2026-10-08T15:00:00.123Z",
            "2026-10-08T15:00:00.123456789Z,2026-10-08T15:00:00.123Z"})
    void detailFormatsUtcMillisecondsWithoutChangingSuppliedInstant(String timestamp, String expected)
            throws Exception {
        var instant = Instant.parse(timestamp);
        var detail = new OwnOrderDetailDTO(1L, instant, OrderStatus.WAITING_PAYMENT, "1.00",
                List.of(new OwnOrderItemDTO(8L, "Produto atual", 1, "1.00", "1.00")));
        assertText(json(detail).get("createdAt"), expected);
        assertThat(detail.createdAt()).isEqualTo(instant);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "0.10", "0.99", "1.00", "42.50", "10000000.00",
            "9007199254740993.01", "99999999999999999.98", "99999999999999999.99"})
    void detailPreservesExactMoneyInEveryFinancialField(String amount) throws Exception {
        var item = new OwnOrderItemDTO(8L, "Preço histórico", 1, amount, amount);
        var detail = new OwnOrderDetailDTO(1L, CREATED_AT, OrderStatus.WAITING_PAYMENT, amount, List.of(item));
        assertThat(detail.total()).isEqualTo(amount);
        assertThat(item.unitPrice()).isEqualTo(amount);
        assertThat(item.subtotal()).isEqualTo(amount);
        var value = json(detail);
        assertDetailHeader(value, 1L, amount);
        assertThat(value.get("items").size()).isEqualTo(1);
        assertItem(value.get("items").get(0), 8L, "Preço histórico", 1, amount, amount);
    }

    @ParameterizedTest(name = "invalid financial field [{index}] {0}, {1}")
    @MethodSource("detailInvalidMoneyCases")
    void detailRejectsNonContractMoneyInEveryFinancialField(String field, String amount) {
        assertThatThrownBy(() -> {
            switch (field) {
                case "unitPrice" -> new OwnOrderItemDTO(8L, "Produto atual", 1, amount, "1.00");
                case "subtotal" -> new OwnOrderItemDTO(8L, "Produto atual", 1, "1.00", amount);
                case "total" -> new OwnOrderDetailDTO(1L, CREATED_AT, OrderStatus.WAITING_PAYMENT, amount,
                        List.of(new OwnOrderItemDTO(8L, "Produto atual", 1, "1.00", "1.00")));
                default -> throw new AssertionError("Unknown financial field in the test provider");
            }
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "current product name [{index}]")
    @MethodSource("detailCurrentNames")
    void detailPreservesCurrentProductNameWithoutAdministrativeWriteValidation(String name) throws Exception {
        var item = new OwnOrderItemDTO(8L, name, 2, "10.00", "20.00");
        var detail = new OwnOrderDetailDTO(1L, CREATED_AT, OrderStatus.WAITING_PAYMENT, "20.00", List.of(item));
        assertThat(item.productName()).isEqualTo(name);
        assertItem(json(detail).get("items").get(0), 8L, name, 2, "10.00", "20.00");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(item)).isEmpty();
            assertThat(factory.getValidator().validate(detail)).isEmpty();
        }
    }

    @Test
    void detailKeepsProductsWithEqualNamesDistinctInSuppliedOrder() throws Exception {
        var detail = new OwnOrderDetailDTO(1L, CREATED_AT, OrderStatus.WAITING_PAYMENT, "42.50",
                List.of(new OwnOrderItemDTO(8L, " Nome igual ", 2, "10.00", "20.00"),
                        new OwnOrderItemDTO(3L, " Nome igual ", 3, "7.50", "22.50")));
        var items = json(detail).get("items");
        assertThat(items.size()).isEqualTo(2);
        assertItem(items.get(0), 8L, " Nome igual ", 2, "10.00", "20.00");
        assertItem(items.get(1), 3L, " Nome igual ", 3, "7.50", "22.50");
    }

    @Test
    void detailDefensivelyCopiesAndProtectsSuppliedItems() throws Exception {
        var b = new OwnOrderItemDTO(8L, "B", 2, "10.00", "20.00");
        var a = new OwnOrderItemDTO(3L, "A", 3, "7.50", "22.50");
        var supplied = new ArrayList<>(List.of(b, a));
        var detail = new OwnOrderDetailDTO(1L, CREATED_AT, OrderStatus.WAITING_PAYMENT, "42.50", supplied);
        supplied.set(0, a);
        supplied.clear();
        assertThat(detail.items()).containsExactly(b, a);
        assertThatThrownBy(() -> detail.items().add(b)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> detail.items().set(0, a)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> detail.items().remove(0)).isInstanceOf(UnsupportedOperationException.class);
        var items = json(detail).get("items");
        assertThat(items.size()).isEqualTo(2);
        assertItem(items.get(0), 8L, "B", 2, "10.00", "20.00");
        assertItem(items.get(1), 3L, "A", 3, "7.50", "22.50");
    }

    @Test
    void detailPreservesAllSuppliedItemsWithoutBorrowingThePageLimit() throws Exception {
        var supplied = new ArrayList<OwnOrderItemDTO>();
        for (long id = 1; id <= 51; id++) {
            supplied.add(new OwnOrderItemDTO(id, "Produto " + id, 1, "0.01", "0.01"));
        }
        var detail = new OwnOrderDetailDTO(1L, CREATED_AT, OrderStatus.WAITING_PAYMENT, "0.51", supplied);
        var value = json(detail);
        assertDetailHeader(value, 1L, "0.51");
        assertThat(detail.items()).hasSize(51);
        assertThat(value.get("items").size()).isEqualTo(51);
        for (int index = 0; index < 51; index++) {
            assertItem(value.get("items").get(index), index + 1L, "Produto " + (index + 1), 1, "0.01", "0.01");
        }
    }

    @Test
    void detailRejectsNullItemCollectionAndNullEntries() {
        assertThatThrownBy(() -> new OwnOrderDetailDTO(1L, CREATED_AT, OrderStatus.WAITING_PAYMENT, "1.00", null))
                .isInstanceOf(NullPointerException.class);
        var supplied = new ArrayList<OwnOrderItemDTO>();
        supplied.add(null);
        assertThatThrownBy(() -> new OwnOrderDetailDTO(1L, CREATED_AT, OrderStatus.WAITING_PAYMENT, "1.00", supplied))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void detailRoundTripPreservesExactValuesAndItemSequence() throws Exception {
        var original = new OwnOrderDetailDTO(Long.MAX_VALUE, CREATED_AT, OrderStatus.WAITING_PAYMENT,
                "9007199254740993.02", List.of(
                        new OwnOrderItemDTO(8L, " Café B ", 1, "9007199254740993.01", "9007199254740993.01"),
                        new OwnOrderItemDTO(3L, "Á", 1, "0.01", "0.01")));
        var bytes = mapper.writeValueAsBytes(original);
        var restored = mapper.readValue(bytes, OwnOrderDetailDTO.class);
        assertThat(restored).isEqualTo(original);
        assertThatThrownBy(() -> restored.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        var value = mapper.readTree(bytes);
        assertDetailHeader(value, Long.MAX_VALUE, "9007199254740993.02");
        assertItem(value.get("items").get(0), 8L, " Café B ", 1, "9007199254740993.01", "9007199254740993.01");
        assertItem(value.get("items").get(1), 3L, "Á", 1, "0.01", "0.01");
    }

    @Test
    void detailSharesOnlyTheFourSummaryHeaderFields() throws Exception {
        var summary = summary(9L, "20.00");
        var detail = new OwnOrderDetailDTO(9L, CREATED_AT, OrderStatus.WAITING_PAYMENT, "20.00",
                List.of(new OwnOrderItemDTO(8L, "Nome atual", 2, "10.00", "20.00")));
        var summaryJson = json(summary);
        var detailJson = json(detail);
        assertSummary(summaryJson, 9L, "20.00");
        assertDetailHeader(detailJson, 9L, "20.00");
        for (var field : List.of("id", "createdAt", "status", "total")) {
            assertThat(detailJson.get(field)).isEqualTo(summaryJson.get(field));
        }
    }

    static Stream<Arguments> detailInvalidMoneyCases() {
        return Stream.of("unitPrice", "subtotal", "total").flatMap(field -> Stream.concat(
                Stream.of((String) null),
                Stream.of("", "0.00", "-1.00", "1", "1.0", "1.000", "1.001", "1e2", "1E+2", "01.00",
                        "1,00", "1,000.00", "100000000000000000.00", " 1.00", "1.00 ", "+1.00", "NaN", "Infinity"))
                .map(amount -> Arguments.of(field, amount)));
    }

    static Stream<String> detailCurrentNames() {
        return Stream.of("Á", " " + "Café ".repeat(17) + " ", " Produto  com espaços ", "Cafe\u0301");
    }

    private static void assertDetailHeader(JsonNode value, long id, String total) {
        assertFields(value, "id", "createdAt", "status", "total", "items");
        assertInteger(value.get("id"), id);
        assertText(value.get("createdAt"), "2026-10-08T15:00:00.123Z");
        assertText(value.get("status"), "WAITING_PAYMENT");
        assertText(value.get("total"), total);
    }

    private static void assertItem(JsonNode value, long productId, String name, int quantity,
                                   String unitPrice, String subtotal) {
        assertFields(value, "productId", "productName", "quantity", "unitPrice", "subtotal");
        assertInteger(value.get("productId"), productId);
        assertText(value.get("productName"), name);
        assertInteger(value.get("quantity"), quantity);
        assertText(value.get("unitPrice"), unitPrice);
        assertText(value.get("subtotal"), subtotal);
    }

    private static void assertText(JsonNode value, String expected) {
        assertThat(value.isTextual()).isTrue();
        assertThat(value.textValue()).isEqualTo(expected);
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
