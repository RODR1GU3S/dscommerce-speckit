package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.PaymentRegistrationRequestDTO;
import com.devsuperior.dscommerce.entities.*;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import com.devsuperior.dscommerce.repositories.OrderRepository.PaymentItemView;
import com.devsuperior.dscommerce.repositories.PaymentRepository;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.repositories.UserAccountRepository.EffectiveAccountRole;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException.Reason.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Local rules and orchestration only: no database, real lock wait, transaction commit or HTTP. */
class PaymentRegistrationServiceTest {
    private static final long ORDER_ID = 71;
    private static final Instant CREATED = Instant.parse("2026-10-21T10:00:00Z");
    private static final Instant OCCURRED = Instant.parse("2026-10-21T11:00:00.123Z");
    private static final Instant REFERENCE = Instant.parse("2026-10-21T12:00:00.123456789Z");

    @Nested
    class CurrentAccess {
        private UserAccountRepository accounts;
        private PaymentAccessService access;

        @BeforeEach
        void setup() {
            accounts = mock(UserAccountRepository.class);
            access = new PaymentAccessService(accounts);
        }

        @ParameterizedTest
        @ValueSource(strings = {"ADMIN", "BOTH"})
        void currentAdminIsSufficientWithoutTrustingTokenAuthorities(String roles) {
            when(accounts.findEffectiveAccountRolesById(42L)).thenReturn(roles.equals("ADMIN")
                    ? List.of(role(42L, UserRole.ADMIN))
                    : List.of(role(42L, UserRole.CLIENT), role(42L, UserRole.ADMIN)));
            access.requireAdmin(authentication(42));
            verify(accounts).findEffectiveAccountRolesById(42L);
            verifyNoMoreInteractions(accounts);
        }

        @ParameterizedTest
        @ValueSource(strings = {"CLIENT", "NONE"})
        void tokenAdminCannotReplaceTheCurrentScalarRole(String roles) {
            when(accounts.findEffectiveAccountRolesById(42L)).thenReturn(
                    List.of(role(42L, roles.equals("CLIENT") ? UserRole.CLIENT : null)));
            var auth = new UsernamePasswordAuthenticationToken(new AuthenticatedAccountPrincipal(42L), "unused",
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            failure(() -> access.requireAdmin(auth), FORBIDDEN);
            verify(accounts).findEffectiveAccountRolesById(42L);
            verifyNoMoreInteractions(accounts);
        }

        @Test
        void absentAccountIsAuthenticationFailureRatherThanPermissionOrLookupFailure() {
            when(accounts.findEffectiveAccountRolesById(42L)).thenReturn(List.of());
            failure(() -> access.requireAdmin(authentication(42)), UNAUTHENTICATED);
            verify(accounts).findEffectiveAccountRolesById(42L);
            verifyNoMoreInteractions(accounts);
        }

        @ParameterizedTest
        @MethodSource("com.devsuperior.dscommerce.services.PaymentRegistrationServiceTest#invalidAuthentications")
        void invalidIdentityIsRejectedWithoutAnyLookup(Authentication auth) {
            failure(() -> access.requireAdmin(auth), UNAUTHENTICATED);
            verifyNoInteractions(accounts);
        }

        @Test
        void everyGuardReadsScalarRolesAgainIncludingRevocation() {
            when(accounts.findEffectiveAccountRolesById(42L)).thenReturn(
                    List.of(role(42L, UserRole.ADMIN)), List.of(role(42L, UserRole.CLIENT)));
            access.requireAdmin(authentication(42));
            failure(() -> access.requireAdmin(authentication(42)), FORBIDDEN);
            verify(accounts, times(2)).findEffectiveAccountRolesById(42L);
            verifyNoMoreInteractions(accounts);
        }

        @Test
        void failedLookupRetainsItsCauseAndSafeProcessingCategoryWithoutRetry() {
            var cause = new DataAccessResourceFailureException("private SQL/credential detail");
            when(accounts.findEffectiveAccountRolesById(42L)).thenThrow(cause);
            assertThatThrownBy(() -> access.requireAdmin(authentication(42)))
                    .isInstanceOfSatisfying(PaymentRegistrationException.class, error -> {
                        assertThat(error.getReason()).isEqualTo(PROCESSING_FAILURE);
                        assertThat(error.getStatus()).isEqualTo(500);
                        assertThat(error.getMessage()).isEqualTo("Payment registration could not be completed");
                        assertThat(error.getCause()).isSameAs(cause);
                    });
            verify(accounts).findEffectiveAccountRolesById(42L);
            verifyNoMoreInteractions(accounts);
        }

        @ParameterizedTest
        @ValueSource(strings = {"wrong-id", "null-id", "null-row", "null-list"})
        void inconsistentProjectionIsInternalFailureRatherThanMissingAccount(String corruption) {
            List<EffectiveAccountRole> rows = switch (corruption) {
                case "wrong-id" -> List.of(role(43L, UserRole.ADMIN));
                case "null-id" -> List.of(role(null, UserRole.ADMIN));
                case "null-row" -> Collections.singletonList(null);
                default -> null;
            };
            when(accounts.findEffectiveAccountRolesById(42L)).thenReturn(rows);
            failure(() -> access.requireAdmin(authentication(42)), PROCESSING_FAILURE);
            verify(accounts).findEffectiveAccountRolesById(42L);
            verifyNoMoreInteractions(accounts);
        }
    }

    @Nested
    class RegistrationRules {
        private Clock clock;
        private PaymentAccessService access;
        private OrderRepository orders;
        private PaymentRepository payments;
        private EntityManager entityManager;
        private PaymentRegistrationService service;
        private Authentication auth;
        private Order order;
        private Product product;
        private UserAccount buyer;

        @BeforeEach
        void setup() {
            clock = mock(Clock.class);
            access = mock(PaymentAccessService.class);
            orders = mock(OrderRepository.class);
            payments = mock(PaymentRepository.class);
            entityManager = mock(EntityManager.class);
            service = new PaymentRegistrationService(clock, access, orders, payments, entityManager);
            auth = authentication(42);
            buyer = mock(UserAccount.class);
            product = mock(Product.class);
            order = spy(new Order(buyer, CREATED, List.of(new OrderItem(product, 2, new BigDecimal("10.00")))));
            ReflectionTestUtils.setField(order, "id", ORDER_ID);
            when(clock.instant()).thenReturn(REFERENCE);
            when(orders.findPaymentOrderForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(payments.findById(ORDER_ID)).thenReturn(Optional.empty());
            when(orders.findPaymentItemsByOrderId(ORDER_ID)).thenReturn(history());
        }

        @Test
        void nativeReferenceComesFirstAndCompleteHistoricalSettlementPreservesTheAggregate() {
            var originalItems = List.copyOf(order.getItems());
            clearInvocations(order);
            var sequence = inOrder(clock, access, orders, payments, order, entityManager);
            doAnswer(call -> {
                Payment inserted = call.getArgument(0);
                assertThat(inserted.getOrder()).isSameAs(order);
                assertThat(inserted.getOccurredAt()).isEqualTo(OCCURRED);
                assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_PAYMENT);
                return null;
            }).when(payments).insert(any(Payment.class));

            var result = service.register(ORDER_ID, new PaymentRegistrationRequestDTO(OCCURRED), auth);

            sequence.verify(clock).instant();
            sequence.verify(access).requireAdmin(auth);
            sequence.verify(orders).findPaymentOrderForUpdate(ORDER_ID);
            sequence.verify(access).requireAdmin(auth);
            sequence.verify(payments).findById(ORDER_ID);
            sequence.verify(orders).findPaymentItemsByOrderId(ORDER_ID);
            sequence.verify(payments).insert(any(Payment.class));
            sequence.verify(order).markPaid();
            sequence.verify(entityManager).flush();
            sequence.verify(order).getStatus();
            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            assertThat(result.occurredAt()).isEqualTo(OCCURRED);
            assertThat(result.status()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getBuyer()).isSameAs(buyer);
            assertThat(order.getCreatedAt()).isEqualTo(CREATED);
            assertThat(order.getItems()).containsExactlyElementsOf(originalItems);
            assertThat(order.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("10.00");
            assertThat(order.getItems().getFirst().getQuantity()).isEqualTo(2);
            verify(order, never()).getTotal();
            verifyNoInteractions(product, buyer);
            verify(clock).instant();
            verifyNoMoreInteractions(clock, orders, payments, entityManager);
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-10-21T10:00:00Z", "2026-10-21T12:00:00.123Z"})
        void inclusiveBoundsRemainValidWhenTheClockAdvancesDuringControlledLookup(String input) {
            var fixed = Instant.parse("2026-10-21T12:00:00.123Z");
            var now = new AtomicReference<>(fixed);
            when(clock.instant()).thenAnswer(call -> now.get());
            when(orders.findPaymentOrderForUpdate(ORDER_ID)).thenAnswer(call -> {
                now.set(fixed.plusSeconds(3600));
                return Optional.of(order);
            });
            var result = service.register(ORDER_ID, new PaymentRegistrationRequestDTO(Instant.parse(input)), auth);
            assertThat(result.occurredAt()).isEqualTo(Instant.parse(input));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(clock).instant();
            verify(access, times(2)).requireAdmin(auth);
        }

        @Test
        void elapsedLookupTimeCannotMakeAnOccurrenceAfterTheInitialReferenceValid() {
            var now = new AtomicReference<>(REFERENCE);
            when(clock.instant()).thenAnswer(call -> now.get());
            when(orders.findPaymentOrderForUpdate(ORDER_ID)).thenAnswer(call -> {
                now.set(REFERENCE.plusSeconds(3600));
                return Optional.of(order);
            });
            failure(() -> register(Instant.parse("2026-10-21T12:00:00.124Z")), INVALID_INPUT);
            unchangedBeforeWrite();
            verify(clock).instant();
            verify(orders, never()).findPaymentItemsByOrderId(anyLong());
        }

        @Test
        void nativeNanosecondsAreComparedWithoutRoundingTheReferenceUp() {
            when(clock.instant()).thenReturn(Instant.parse("2026-10-21T12:00:00.123999999Z"));
            failure(() -> register(Instant.parse("2026-10-21T12:00:00.124Z")), INVALID_INPUT);
            unchangedBeforeWrite();
            verify(clock).instant();
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-10-21T09:59:59.999Z", "2026-10-21T12:00:00.124Z",
                "2026-10-21T11:00:00.123000001Z", "0000-12-31T23:59:59Z", "+10000-01-01T00:00:00Z"})
        void invalidOccurrenceNeverMutatesOrSilentlyRounds(String input) {
            failure(() -> register(Instant.parse(input)), INVALID_INPUT);
            unchangedBeforeWrite();
        }

        @Test
        void nullOccurrenceIsInvalidInputEvenForDirectServiceCalls() {
            failure(() -> register(null), INVALID_INPUT);
            unchangedBeforeWrite();
        }

        @ParameterizedTest
        @ValueSource(strings = {"UNAUTHENTICATED", "FORBIDDEN", "PROCESSING_FAILURE"})
        void firstGuardFailurePreventsEveryOrderRead(String reason) {
            var rejection = new PaymentRegistrationException(PaymentRegistrationException.Reason.valueOf(reason));
            doThrow(rejection).when(access).requireAdmin(auth);
            assertThatThrownBy(() -> register(OCCURRED)).isSameAs(rejection);
            var sequence = inOrder(clock, access);
            sequence.verify(clock).instant();
            sequence.verify(access).requireAdmin(auth);
            verifyNoInteractions(orders, payments, entityManager);
            verify(order, never()).markPaid();
        }

        @ParameterizedTest
        @ValueSource(strings = {"UNAUTHENTICATED", "FORBIDDEN", "PROCESSING_FAILURE"})
        void revalidationAfterRootAcquisitionPreventsWritesOnRevocationOrLookupFailure(String reason) {
            var rejection = new PaymentRegistrationException(PaymentRegistrationException.Reason.valueOf(reason));
            doNothing().doThrow(rejection).when(access).requireAdmin(auth);
            assertThatThrownBy(() -> register(OCCURRED)).isSameAs(rejection);
            var sequence = inOrder(clock, access, orders);
            sequence.verify(clock).instant();
            sequence.verify(access).requireAdmin(auth);
            sequence.verify(orders).findPaymentOrderForUpdate(ORDER_ID);
            sequence.verify(access).requireAdmin(auth);
            verifyNoInteractions(payments, entityManager);
            verify(order, never()).markPaid();
        }

        @Test
        void absentOrderIsNotFoundWithoutAnyPaymentHistoryOrWrite() {
            when(orders.findPaymentOrderForUpdate(ORDER_ID)).thenReturn(Optional.empty());
            failure(() -> register(OCCURRED), ORDER_NOT_FOUND);
            verifyNoInteractions(payments, entityManager);
            verify(orders, never()).findPaymentItemsByOrderId(anyLong());
        }

        @ParameterizedTest
        @ValueSource(strings = {"WAITING_PAYMENT", "PAID"})
        void existingPaymentAlwaysPrecedesIncompatibleStateAndIsNeverReusedOrRepaired(String status) {
            ReflectionTestUtils.setField(order, "status", OrderStatus.valueOf(status));
            var original = new Payment(order, CREATED);
            when(payments.findById(ORDER_ID)).thenReturn(Optional.of(original));
            failure(() -> register(OCCURRED), ALREADY_REGISTERED);
            assertThat(original.getOccurredAt()).isEqualTo(CREATED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.valueOf(status));
            verify(payments, never()).insert(any());
            verify(order, never()).markPaid();
            verifyNoInteractions(entityManager);
            verify(orders, never()).findPaymentItemsByOrderId(anyLong());
        }

        @Test
        void paidWithoutPaymentIsASeparateConflictWithoutAutomaticRepair() {
            ReflectionTestUtils.setField(order, "status", OrderStatus.PAID);
            failure(() -> register(OCCURRED), INCOMPATIBLE_STATE);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(payments, never()).insert(any());
            verify(order, never()).markPaid();
            verifyNoInteractions(entityManager);
            verify(orders, never()).findPaymentItemsByOrderId(anyLong());
        }

        @ParameterizedTest
        @ValueSource(strings = {"missing-created", "wrong-id", "missing-status"})
        void corruptRootIsProcessingFailureWithoutRepair(String corruption) {
            switch (corruption) {
                case "missing-created" -> ReflectionTestUtils.setField(order, "createdAt", null);
                case "wrong-id" -> ReflectionTestUtils.setField(order, "id", 72L);
                default -> ReflectionTestUtils.setField(order, "status", null);
            }
            failure(() -> register(OCCURRED), PROCESSING_FAILURE);
            verify(payments, never()).insert(any());
            verify(order, never()).markPaid();
            verifyNoInteractions(entityManager);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.devsuperior.dscommerce.services.PaymentRegistrationServiceTest#corruptHistories")
        void historicalCorruptionIsInternalFailureBeforeMutation(String label, List<PaymentItemView> rows) {
            when(orders.findPaymentItemsByOrderId(ORDER_ID)).thenReturn(rows);
            failure(() -> register(OCCURRED), PROCESSING_FAILURE);
            unchangedBeforeWrite();
            verifyNoInteractions(product, buyer);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.0100", "99999999999999999.99"})
        void exactHistoricalMoneyAcceptsTrailingZerosAndTheDecimal19Scale2Limit(String price) {
            when(orders.findPaymentItemsByOrderId(ORDER_ID)).thenReturn(List.of(item(1L, 0, 1, price)));
            assertThat(register(OCCURRED).status()).isEqualTo(OrderStatus.PAID);
            verifyNoInteractions(product, buyer);
        }

        @ParameterizedTest
        @ValueSource(strings = {"lock", "payment-lookup", "history", "persist", "flush"})
        void operationalFailuresPropagateUnchangedWithoutRetryOrRecoveryQueries(String stage) {
            RuntimeException cause = stage.equals("persist")
                    ? new DataIntegrityViolationException("unexpected PK/FK")
                    : new DataAccessResourceFailureException("operational failure");
            switch (stage) {
                case "lock" -> when(orders.findPaymentOrderForUpdate(ORDER_ID)).thenThrow(cause);
                case "payment-lookup" -> when(payments.findById(ORDER_ID)).thenThrow(cause);
                case "history" -> when(orders.findPaymentItemsByOrderId(ORDER_ID)).thenThrow(cause);
                case "persist" -> doThrow(cause).when(payments).insert(any());
                default -> doThrow(cause).when(entityManager).flush();
            }
            assertThatThrownBy(() -> register(OCCURRED)).isSameAs(cause);
            var sequence = inOrder(orders, payments, entityManager);
            sequence.verify(orders).findPaymentOrderForUpdate(ORDER_ID);
            if (!stage.equals("lock")) { sequence.verify(payments).findById(ORDER_ID); }
            if (List.of("history", "persist", "flush").contains(stage)) {
                sequence.verify(orders).findPaymentItemsByOrderId(ORDER_ID);
            }
            if (List.of("persist", "flush").contains(stage)) { sequence.verify(payments).insert(any()); }
            if (stage.equals("flush")) { sequence.verify(entityManager).flush(); }
            sequence.verifyNoMoreInteractions();
            if (!stage.equals("flush")) { verify(order, never()).markPaid(); }
            // A mutated mock/entity after flush failure is not proof of a committed database state.
        }

        private com.devsuperior.dscommerce.dto.PaymentConfirmationDTO register(Instant occurrence) {
            return service.register(ORDER_ID, new PaymentRegistrationRequestDTO(occurrence), auth);
        }

        private void unchangedBeforeWrite() {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_PAYMENT);
            verify(order, never()).markPaid();
            verify(payments, never()).insert(any());
            verifyNoInteractions(entityManager);
        }
    }

    @Nested
    class TransactionAndCategories {
        @Test
        void registrationDeclaresRequiredReadCommittedAndRollbackForCheckedExceptions() throws Exception {
            var method = PaymentRegistrationService.class.getMethod("register", long.class,
                    PaymentRegistrationRequestDTO.class, Authentication.class);
            var attribute = new AnnotationTransactionAttributeSource()
                    .getTransactionAttribute(method, PaymentRegistrationService.class);
            assertThat(attribute).isNotNull();
            assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
            assertThat(attribute.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
            assertThat(attribute.isReadOnly()).isFalse();
            assertThat(attribute.rollbackOn(new Exception("checked"))).isTrue();
            assertThat(attribute.rollbackOn(new DataIntegrityViolationException("db"))).isTrue();
        }

        @ParameterizedTest
        @CsvSource({"INVALID_INPUT,400,Invalid payment data",
                "UNAUTHENTICATED,401,Authentication required or invalid",
                "FORBIDDEN,403,Payment registration not permitted", "ORDER_NOT_FOUND,404,Order not found",
                "ALREADY_REGISTERED,409,Payment already registered",
                "INCOMPATIBLE_STATE,409,Order is not awaiting payment",
                "PROCESSING_FAILURE,500,Payment registration could not be completed"})
        void domainFailuresKeepDistinctFixedCategoriesAndMessages(String reason, int status, String message) {
            var error = new PaymentRegistrationException(PaymentRegistrationException.Reason.valueOf(reason));
            assertThat(error.getReason().name()).isEqualTo(reason);
            assertThat(error.getStatus()).isEqualTo(status);
            assertThat(error.getMessage()).isEqualTo(message);
        }
    }

    private static void failure(ThrowingCallable action, PaymentRegistrationException.Reason reason) {
        assertThatThrownBy(action).isInstanceOfSatisfying(PaymentRegistrationException.class, error -> {
            assertThat(error.getReason()).isEqualTo(reason);
            assertThat(error.getStatus()).isEqualTo(reason.getStatus());
        });
    }

    static Stream<Arguments> invalidAuthentications() {
        return Stream.of(null, new UsernamePasswordAuthenticationToken(new AuthenticatedAccountPrincipal(42L), "unused"),
                new UsernamePasswordAuthenticationToken("42", "unused", List.of()),
                new AnonymousAuthenticationToken("key", new AuthenticatedAccountPrincipal(42L),
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))),
                authentication(null), authentication(0L), authentication(-1L)).map(Arguments::of);
    }

    private static Authentication authentication(Long id) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedAccountPrincipal(id), "unused", List.of());
    }

    private static Authentication authentication(long id) { return authentication(Long.valueOf(id)); }

    private static EffectiveAccountRole role(Long id, UserRole role) {
        return new EffectiveAccountRole() {
            public Long getId() { return id; }
            public UserRole getRole() { return role; }
        };
    }

    private static List<PaymentItemView> history() {
        return List.of(item(1L, 0, 2, "10.00"), item(2L, 1, 3, "7.50"));
    }

    private static PaymentItemView item(Long productId, Integer position, Integer quantity, String price) {
        return new PaymentItemView() {
            public Long getProductId() { return productId; }
            public Integer getPosition() { return position; }
            public Integer getQuantity() { return quantity; }
            public BigDecimal getUnitPrice() { return price == null ? null : new BigDecimal(price); }
        };
    }

    static Stream<Arguments> corruptHistories() {
        return Stream.of(
                Arguments.of("null list", null), Arguments.of("empty", List.of()),
                Arguments.of("null row", Collections.singletonList(null)),
                Arguments.of("null product", List.of(item(null, 0, 1, "1"))),
                Arguments.of("invalid product", List.of(item(0L, 0, 1, "1"))),
                Arguments.of("duplicate product", List.of(item(1L, 0, 1, "1"), item(1L, 1, 1, "1"))),
                Arguments.of("null position", List.of(item(1L, null, 1, "1"))),
                Arguments.of("negative position", List.of(item(1L, -1, 1, "1"))),
                Arguments.of("position gap", List.of(item(1L, 1, 1, "1"))),
                Arguments.of("repeated position", List.of(item(1L, 0, 1, "1"), item(2L, 0, 1, "1"))),
                Arguments.of("null quantity", List.of(item(1L, 0, null, "1"))),
                Arguments.of("zero quantity", List.of(item(1L, 0, 0, "1"))),
                Arguments.of("negative quantity", List.of(item(1L, 0, -1, "1"))),
                Arguments.of("null price", List.of(item(1L, 0, 1, null))),
                Arguments.of("zero price", List.of(item(1L, 0, 1, "0"))),
                Arguments.of("negative price", List.of(item(1L, 0, 1, "-1"))),
                Arguments.of("fractional cent", List.of(item(1L, 0, 1, "0.011"))),
                Arguments.of("unit price overflow", List.of(item(1L, 0, 1, "100000000000000000.00"))),
                Arguments.of("subtotal overflow", List.of(item(1L, 0, 2, "99999999999999999.99"))),
                Arguments.of("sum overflow", List.of(item(1L, 0, 1, "99999999999999999.99"), item(2L, 1, 1, "0.01")))
        );
    }
}
