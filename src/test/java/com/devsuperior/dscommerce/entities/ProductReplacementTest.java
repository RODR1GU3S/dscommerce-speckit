package com.devsuperior.dscommerce.entities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T047: entity operation only, with no Spring context, database, authorization or HTTP claims. */
class ProductReplacementTest {
    private static final String NAME = "Produto  substituido";
    private static final String DESCRIPTION = "Descricao  substituida completa";
    private static final String IMAGE = "https://example.invalid/replaced.jpg";
    private static final BigDecimal PRICE = new BigDecimal("12.00");

    @Test
    void replacesAllFiveDataOnTheSameInstanceKeepingIdentityAndRelatedCategories() {
        var target = new Product(11L, "ab", "Legado", "imagem-legada", new BigDecimal("0.00"));
        var oldA = new Category(1L, "Antiga A");
        var oldB = new Category(2L, "Antiga B");
        target.getCategories().addAll(List.of(oldA, oldB));
        var other = new Product(12L, "Outro produto", "Descricao de outro produto",
                "https://example.invalid/other.jpg", new BigDecimal("20.00"));
        other.getCategories().add(oldA);
        var otherBefore = state(other);
        var newA = new Category(3L, "Nova igual");
        var newB = new Category(4L, "Nova igual");
        var originalCollection = target.getCategories();
        var originalHashCode = target.hashCode();

        target.replaceDetails(NAME, DESCRIPTION, IMAGE, PRICE, List.of(newA, newB));

        assertReplaced(target, Set.of(newA, newB));
        assertThat(target.getCategories()).isSameAs(originalCollection);
        assertThat(target.hashCode()).isEqualTo(originalHashCode);
        assertThat(target.getCategories().stream().filter(category -> category.getId().equals(3L)).findFirst().orElseThrow())
                .isSameAs(newA);
        assertThat(target.getCategories().stream().filter(category -> category.getId().equals(4L)).findFirst().orElseThrow())
                .isSameAs(newB);
        assertThat(oldA.getId()).isEqualTo(1L);
        assertThat(oldA.getName()).isEqualTo("Antiga A");
        assertThat(oldB.getId()).isEqualTo(2L);
        assertThat(oldB.getName()).isEqualTo("Antiga B");
        assertThat(state(other)).isEqualTo(otherBefore);
        assertThat(other.getCategories().iterator().next()).isSameAs(oldA);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void consolidatesRepeatedIdsAndDoesNotRetainTheCallersCollection(boolean includeSecondId) {
        var target = original();
        var a = new Category(3L, "Mesmo nome");
        var sameA = new Category(3L, "Mesmo nome");
        var b = new Category(4L, "Mesmo nome");
        var supplied = new ArrayList<>(List.of(a, sameA));
        if (includeSecondId) supplied.add(b);

        target.replaceDetails(NAME, DESCRIPTION, IMAGE, PRICE, supplied);
        supplied.clear();

        assertReplaced(target, includeSecondId ? Set.of(a, b) : Set.of(a));
        assertThat(target.getCategories().stream().map(Category::getId)).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void copiesAnAliasedSelectionBeforeClearingTheExistingCollection(boolean useView) {
        var target = original();
        var expected = Set.copyOf(target.getCategories());
        var originalCollection = target.getCategories();
        Collection<Category> supplied = useView ? Collections.unmodifiableSet(originalCollection) : originalCollection;

        target.replaceDetails(NAME, DESCRIPTION, IMAGE, PRICE, supplied);

        assertReplaced(target, expected);
        assertThat(target.getCategories()).isSameAs(originalCollection);
    }

    @Test
    void successiveReplacementRemovesAssociationsFromThePreviousSelection() {
        var target = original();
        var a = new Category(3L, "A");
        var b = new Category(4L, "B");
        target.replaceDetails(NAME, DESCRIPTION, IMAGE, PRICE, List.of(a, b));

        target.replaceDetails("Segunda substituicao", "Segunda descricao completa", IMAGE, new BigDecimal("15.00"), List.of(b));

        assertThat(state(target)).isEqualTo(new State(11L, "Segunda substituicao", "Segunda descricao completa",
                IMAGE, new BigDecimal("15.00"), Set.of(b)));
        assertThat(a.getId()).isEqualTo(3L);
        assertThat(a.getName()).isEqualTo("A");
    }

    @Test
    void catalogPriceChangeDoesNotRewriteHistoricalItemValuesOrOrderOwnershipInMemory() {
        var target = original();
        var historicalPrice = new BigDecimal("10.00");
        var item = new OrderItem(target, 2, historicalPrice);
        var buyer = new UserAccount(7L, "unit-buyer", "unused-unit-hash");
        var createdAt = Instant.parse("2026-10-15T12:00:00Z");
        var order = new Order(buyer, createdAt, List.of(item));

        target.replaceDetails(NAME, DESCRIPTION, IMAGE, PRICE, List.of(new Category(3L, "Nova")));

        assertThat(target.getPrice()).isEqualTo(new BigDecimal("12.00"));
        assertThat(item.getProduct()).isSameAs(target);
        assertThat(item.getUnitPrice()).isSameAs(historicalPrice).isEqualTo(new BigDecimal("10.00"));
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getOrder()).isSameAs(order);
        assertThat(item.getPosition()).isZero();
        var independentSubtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        assertThat(independentSubtotal).isEqualTo(new BigDecimal("20.00"));
        assertThat(order.getItems()).containsExactly(item);
        assertThat(order.getBuyer()).isSameAs(buyer);
        assertThat(order.getCreatedAt()).isEqualTo(createdAt);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_PAYMENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "description", "image", "price", "categories", "categoryElement"})
    void rejectsNullInternalArgumentsBeforeChangingAnyValueOrAssociation(String invalid) {
        var target = original();
        var before = state(target);
        var originalCollection = target.getCategories();
        Collection<Category> selection = invalid.equals("categories") ? null
                : invalid.equals("categoryElement") ? Arrays.asList(new Category(3L, "Nova"), null)
                : List.of(new Category(3L, "Nova"));

        assertThatThrownBy(() -> target.replaceDetails(invalid.equals("name") ? null : NAME,
                invalid.equals("description") ? null : DESCRIPTION, invalid.equals("image") ? null : IMAGE,
                invalid.equals("price") ? null : PRICE, selection)).isInstanceOf(NullPointerException.class);

        assertThat(state(target)).isEqualTo(before);
        assertThat(target.getCategories()).isSameAs(originalCollection);
    }

    private static Product original() {
        var product = new Product(11L, "Nome original", "Descricao original completa",
                "https://example.invalid/original.jpg", new BigDecimal("10.00"));
        product.getCategories().add(new Category(1L, "Antiga"));
        return product;
    }
    private static void assertReplaced(Product product, Set<Category> categories) {
        assertThat(state(product)).isEqualTo(new State(11L, NAME, DESCRIPTION, IMAGE, PRICE, categories));
    }
    private static State state(Product product) {
        return new State(product.getId(), product.getName(), product.getDescription(), product.getImage(),
                product.getPrice(), Set.copyOf(product.getCategories()));
    }
    private record State(Long id, String name, String description, String image, BigDecimal price, Set<Category> categories) { }
}
