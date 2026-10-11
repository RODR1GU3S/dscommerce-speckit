package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.entities.OrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DTO contract only: Boot's JSON mapper, without an order service, endpoint or database. */
@JsonTest
class OrderDtoContractTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-08T15:00:00.123Z");
    private static final OrderItemResponseDTO FIRST = new OrderItemResponseDTO(9L, 2, "10.00", "20.00");
    private static final OrderItemResponseDTO SECOND = new OrderItemResponseDTO(3L, 3, "7.50", "22.50");

    @Autowired
    private ObjectMapper mapper;

    @Test
    void shouldReadAndWriteOnlyTheTypedRequestFieldsWithoutLosingIntegerLimits() throws Exception {
        String input = """
                {"items":[{"productId":9223372036854775807,"quantity":2147483647}]}
                """;
        OrderRequestDTO request = mapper.readValue(input, OrderRequestDTO.class);

        assertThat(request.items()).containsExactly(new OrderItemRequestDTO(Long.MAX_VALUE, Integer.MAX_VALUE));
        JsonNode json = json(request);
        assertFields(json, "items");
        assertThat(json.path("items").isArray()).isTrue();
        assertFields(json.path("items").get(0), "productId", "quantity");
        assertInteger(json.path("items").get(0).path("productId"), Long.MAX_VALUE);
        assertInteger(json.path("items").get(0).path("quantity"), Integer.MAX_VALUE);
    }

    @Test
    void shouldSerializeExactlyTheResponseFieldsAndPreserveLineOrder() throws Exception {
        JsonNode json = json(response(List.of(FIRST, SECOND), "42.50"));

        assertFields(json, "id", "accountId", "createdAt", "status", "items", "total");
        assertInteger(json.path("id"), Long.MAX_VALUE);
        assertInteger(json.path("accountId"), Long.MAX_VALUE - 1);
        assertThat(json.path("status").isTextual()).isTrue();
        assertThat(json.path("status").textValue()).isEqualTo("WAITING_PAYMENT");
        assertThat(json.path("items").isArray()).isTrue();
        assertThat(json.path("items").size()).isEqualTo(2);
        assertLine(json.path("items").get(0), 9L, 2, "10.00", "20.00");
        assertLine(json.path("items").get(1), 3L, 3, "7.50", "22.50");
        assertMoney(json.path("total"), "42.50");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-08T15:00:00.123Z", "2026-10-08T15:00:00.000Z"})
    void shouldSerializeCreationInstantAsUtcTextWithMillisecondPrecision(String timestamp) throws Exception {
        Instant instant = Instant.parse(timestamp);
        var response = new OrderResponseDTO(1L, 2L, instant, OrderStatus.WAITING_PAYMENT, List.of(FIRST), "20.00");
        JsonNode value = json(response).path("createdAt");

        assertThat(value.isTextual()).isTrue();
        assertThat(value.textValue()).isEqualTo(timestamp);
        assertThat(Instant.parse(value.textValue())).isEqualTo(instant);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "0.10", "0.99", "1.00", "42.50", "10000000.00", "99999999999999999.98", "99999999999999999.99"})
    void shouldSerializeExactMoneyStringsFromOneCentToDecimalCapacity(String amount) throws Exception {
        var line = new OrderItemResponseDTO(Long.MAX_VALUE, 1, amount, amount);
        JsonNode json = json(response(List.of(line), amount));

        assertLine(json.path("items").get(0), Long.MAX_VALUE, 1, amount, amount);
        assertMoney(json.path("total"), amount);
    }

    @Test
    void shouldSerializeMaximumQuantityAsAnInteger() throws Exception {
        var line = new OrderItemResponseDTO(1L, Integer.MAX_VALUE, "0.01", "21474836.47");
        JsonNode json = json(response(List.of(line), "21474836.47"));

        assertLine(json.path("items").get(0), 1L, Integer.MAX_VALUE, "0.01", "21474836.47");
        assertMoney(json.path("total"), "21474836.47");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0.00", "-1.00", "1", "1.0", "1.000", "1.001", "1e2", "1E+2", "01.00",
            "1,00", "1,000.00", "100000000000000000.00", " 1.00", "1.00 ", "+1.00", "NaN", "Infinity"})
    void shouldRejectNonContractMoneyInsteadOfRoundingOrReformattingIt(String amount) {
        assertThatThrownBy(() -> new OrderItemResponseDTO(1L, 1, amount, "1.00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderItemResponseDTO(1L, 1, "1.00", amount))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> response(List.of(FIRST), amount))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldKeepResponseIndependentOfTheSourceCollection() throws Exception {
        var source = new ArrayList<>(List.of(FIRST, SECOND));
        OrderResponseDTO response = response(source, "42.50");
        String before = mapper.writeValueAsString(response);

        source.set(0, SECOND);
        source.clear();
        source.add(SECOND);

        assertThat(response.items()).containsExactly(FIRST, SECOND);
        assertThat(mapper.writeValueAsString(response)).isEqualTo(before);
    }

    @Test
    void shouldPreventResponseCollectionMutationThroughItsAccessor() {
        OrderResponseDTO response = response(new ArrayList<>(List.of(FIRST, SECOND)), "42.50");

        assertThatThrownBy(() -> response.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> response.items().add(FIRST)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> response.items().set(0, SECOND)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(response.items()).containsExactly(FIRST, SECOND);
    }

    @Test
    void shouldKeepRequestIndependentOfTheSourceCollectionAndPreventAccessorMutation() {
        var line = new OrderItemRequestDTO(1L, 2);
        var source = new ArrayList<>(List.of(line));
        var request = new OrderRequestDTO(source);

        source.clear();

        assertThat(request.items()).containsExactly(line);
        assertThatThrownBy(() -> request.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.items().add(line)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.items().set(0, line)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRetainUnvalidatedRequestValuesForTheLaterInputValidationTask() {
        assertThat(new OrderRequestDTO(null).items()).isNull();
        assertThat(new OrderRequestDTO(List.of()).items()).isEmpty();
        var incomplete = new OrderItemRequestDTO(null, 0);
        var request = new OrderRequestDTO(Arrays.asList(null, incomplete));

        assertThat(request.items()).containsExactly(null, incomplete);
        assertThat(incomplete.productId()).isNull();
        assertThat(incomplete.quantity()).isZero();
    }

    private OrderResponseDTO response(List<OrderItemResponseDTO> items, String total) {
        return new OrderResponseDTO(Long.MAX_VALUE, Long.MAX_VALUE - 1, CREATED_AT, OrderStatus.WAITING_PAYMENT, items, total);
    }

    private JsonNode json(Object dto) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(dto));
    }

    private static void assertLine(JsonNode line, long productId, int quantity, String unitPrice, String subtotal) {
        assertFields(line, "productId", "quantity", "unitPrice", "subtotal");
        assertInteger(line.path("productId"), productId);
        assertInteger(line.path("quantity"), quantity);
        assertMoney(line.path("unitPrice"), unitPrice);
        assertMoney(line.path("subtotal"), subtotal);
    }

    private static void assertInteger(JsonNode value, long expected) {
        assertThat(value.isIntegralNumber()).isTrue();
        assertThat(value.canConvertToLong()).isTrue();
        assertThat(value.longValue()).isEqualTo(expected);
    }

    private static void assertMoney(JsonNode value, String expected) {
        assertThat(value.isTextual()).isTrue();
        assertThat(value.textValue()).isEqualTo(expected);
    }

    private static void assertFields(JsonNode object, String... expected) {
        assertThat(object.isObject()).isTrue();
        Set<String> fields = new HashSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(expected);
    }
}
