package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.config.AdminProductRequestJsonConfig;
import com.devsuperior.dscommerce.dto.AdminFieldErrorDTO;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic.Field;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic.Representation;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T039 local-policy memory invariants; database/ConstraintValidator evidence is in the integration test. */
class ProductInputValidationServiceTest {
    private final ProductInputValidationService policy = new ProductInputValidationService();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new SimpleModule().addDeserializer(
            ProductWriteRequestDTO.class, new AdminProductRequestJsonConfig.ProductWriteRequestDeserializer()));

    @Test
    void shouldProduceSeparateImmutableNormalizedValuesWithoutChangingRawInputOrMetadata() throws Exception {
        var input = read("{\"name\":\"\u00a0  Fone  Azul \u2003\",\"description\":\"  Descricao  completa  \","
                + "\"price\":\"1.000\",\"image\":\"HTTPS://example.invalid/a?x=1#part\",\"categoryIds\":[1,1,2]}");
        var before = InputState.capture(input);
        var originalDiagnostic = input.diagnostic();
        for (int evaluation = 0; evaluation < 2; evaluation++) {
            var result = policy.validate(input);
            assertThat(InputState.capture(input)).isEqualTo(before);
            assertThat(input.diagnostic()).isSameAs(originalDiagnostic);
            assertThat(result.diagnostic()).isNotSameAs(originalDiagnostic);
            assertThat(result.diagnostic().errors()).isEmpty();
            var values = result.normalizedValues();
            assertThat(values).isNotNull();
            assertThat(values.name()).isEqualTo("Fone  Azul");
            assertThat(values.description()).isEqualTo("Descricao  completa");
            assertThat(values.price()).isEqualTo(new BigDecimal("1.00"));
            assertThat(values.image()).isEqualTo(input.image());
            assertThat(values.categoryIds()).containsExactly(1L, 1L, 2L);
            assertThatThrownBy(() -> values.categoryIds().add(3L)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> result.diagnostic().fields().clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(InputState.capture(input)).isEqualTo(before);
        }
    }

    @Test
    void shouldPreserveStructuralEvidenceWhileAddingAllLocalErrorsToANewDiagnosis() throws Exception {
        var input = read("{\"name\":\" ab \",\"description\":\"123456789\",\"price\":\"0\","
                + "\"image\":\"/relative\",\"categoryIds\":[1,true,9223372036854775807],\"extra\":false}");
        var before = InputState.capture(input);
        assertThat(input.categoryIds()).containsExactly(1L, null, Long.MAX_VALUE);
        assertThat(input.diagnostic().categoryElements().get(1)).isEqualTo(Representation.INVALID_TYPE);
        assertThat(pairs(input.diagnostic().errors())).containsExactlyInAnyOrder(
                "body/UNKNOWN_FIELD", "categoryIds[1]/INVALID_TYPE");
        for (int evaluation = 0; evaluation < 2; evaluation++) {
            var result = policy.validate(input);
            assertThat(result.normalizedValues()).isNull();
            assertThat(pairs(result.diagnostic().errors())).containsExactlyInAnyOrder(
                    "body/UNKNOWN_FIELD", "categoryIds[1]/INVALID_TYPE", "name/INVALID_LENGTH",
                    "description/INVALID_LENGTH", "price/NOT_POSITIVE", "image/INVALID_IMAGE_URL");
            assertThat(result.diagnostic().fields()).isEqualTo(before.fields());
            assertThat(result.diagnostic().categoryElements()).isEqualTo(before.elements());
            assertThat(InputState.capture(input)).isEqualTo(before);
            // Existence of the readable reference is outside local policy, not silently approved here.
            assertThat(result.diagnostic().errors()).noneMatch(error -> error.code().equals("CATEGORY_NOT_FOUND"));
        }
    }

    @Test
    void shouldRetainDuplicateAndInvalidTypeStatesWithoutInventingSecondaryErrors() throws Exception {
        var input = read("{\"name\":\"abc\",\"name\":null,\"description\":false,\"price\":\"1.001\","
                + "\"image\":\"/relative\",\"categoryIds\":[1,true,1]}");
        var before = InputState.capture(input);
        assertThat(before.fields().get(Field.NAME)).isEqualTo(Representation.DUPLICATE);
        assertThat(before.fields().get(Field.DESCRIPTION)).isEqualTo(Representation.INVALID_TYPE);
        var result = policy.validate(input);
        assertThat(pairs(result.diagnostic().errors())).containsExactlyInAnyOrder("name/DUPLICATE_FIELD",
                "description/INVALID_TYPE", "categoryIds[1]/INVALID_TYPE", "price/UNREPRESENTABLE_MONEY",
                "image/INVALID_IMAGE_URL");
        assertThat(result.normalizedValues()).isNull();
        assertThat(InputState.capture(input)).isEqualTo(before);
    }

    @Test
    void shouldKeepTheRequestSnapshotIndependentOfMutableCallerCollectionsDuringEvaluation() {
        var ids = new ArrayList<>(List.of(1L, 1L, 2L));
        var fields = new EnumMap<Field, Representation>(Field.class);
        for (var field : Field.values()) fields.put(field, Representation.READABLE);
        var elements = new HashMap<Integer, Representation>();
        elements.put(0, Representation.READABLE);
        var errors = new ArrayList<AdminFieldErrorDTO>();
        var input = new ProductWriteRequestDTO(" Fone Azul ", " Descricao completa ", "1.000",
                "https://example.invalid/a", ids, new ProductInputDiagnostic(fields, elements, errors));
        var before = InputState.capture(input);
        ids.clear();
        fields.clear();
        elements.clear();
        errors.add(new AdminFieldErrorDTO("body", "UNKNOWN_FIELD", "Unknown field"));
        var result = policy.validate(input);
        assertThat(InputState.capture(input)).isEqualTo(before);
        assertThat(result.diagnostic().errors()).isEmpty();
        assertThat(result.normalizedValues().categoryIds()).containsExactly(1L, 1L, 2L);
        assertThatThrownBy(() -> input.categoryIds().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> input.diagnostic().categoryElements().put(1, Representation.NULL))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(InputState.capture(input)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldLeaveSelectionAndExistenceDecisionsToTheReferenceService(boolean empty) {
        var input = new ProductWriteRequestDTO("Fone Azul", "Descricao completa", "1.000",
                "https://example.invalid/a", empty ? List.of() : List.of(Long.MAX_VALUE));
        var before = InputState.capture(input);
        var result = policy.validate(input);
        assertThat(result.diagnostic().errors()).isEmpty();
        assertThat(result.normalizedValues()).isNotNull();
        assertThat(result.normalizedValues().categoryIds()).isEqualTo(input.categoryIds());
        assertThat(InputState.capture(input)).isEqualTo(before);
        // A local candidate is not proof of authorization, a nonempty selection, existence or commit.
    }

    private ProductWriteRequestDTO read(String json) throws Exception {
        return mapper.readValue(json, ProductWriteRequestDTO.class);
    }

    private static List<String> pairs(List<AdminFieldErrorDTO> errors) {
        return errors.stream().map(error -> error.field() + "/" + error.code()).toList();
    }

    private record InputState(String name, String description, String price, String image, List<Long> ids,
                              Map<Field, Representation> fields, Map<Integer, Representation> elements,
                              List<AdminFieldErrorDTO> errors) {
        static InputState capture(ProductWriteRequestDTO input) {
            return new InputState(input.name(), input.description(), input.price(), input.image(),
                    input.categoryIds() == null ? null : Collections.unmodifiableList(new ArrayList<>(input.categoryIds())),
                    Map.copyOf(input.diagnostic().fields()), Map.copyOf(input.diagnostic().categoryElements()),
                    List.copyOf(input.diagnostic().errors()));
        }
    }
}
