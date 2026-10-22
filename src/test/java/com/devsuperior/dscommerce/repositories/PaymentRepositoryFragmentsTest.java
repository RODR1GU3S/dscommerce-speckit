package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.entities.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Interaction contracts only; physical SQL/transaction observations live in the integration class. */
@ExtendWith(MockitoExtension.class)
class PaymentRepositoryFragmentsTest {
    @Mock private EntityManager entityManager;
    private OrderPaymentLockRepositoryImpl locks;
    private PaymentInsertRepositoryImpl inserts;

    @BeforeEach
    void prepareFragmentsWithAnObservedEntityManager() {
        locks = new OrderPaymentLockRepositoryImpl();
        inserts = new PaymentInsertRepositoryImpl();
        ReflectionTestUtils.setField(locks, "entityManager", entityManager);
        ReflectionTestUtils.setField(inserts, "entityManager", entityManager);
    }

    @Test
    void findThenRefreshBothUsePessimisticWriteWithoutReadingAnyOrderFields() {
        var root = mock(Order.class);
        when(entityManager.find(Order.class, 42L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(root);
        assertThat(locks.findPaymentOrderForUpdate(42L)).hasValue(root);
        var sequence = inOrder(entityManager);
        sequence.verify(entityManager).find(Order.class, 42L, LockModeType.PESSIMISTIC_WRITE);
        sequence.verify(entityManager).refresh(root, LockModeType.PESSIMISTIC_WRITE);
        sequence.verifyNoMoreInteractions();
        verifyNoInteractions(root);
    }

    @Test
    void missingOrderDoesNotRefreshOrAttemptAnotherLookup() {
        assertThat(locks.findPaymentOrderForUpdate(42L)).isEmpty();
        verify(entityManager).find(Order.class, 42L, LockModeType.PESSIMISTIC_WRITE);
        verifyNoMoreInteractions(entityManager);
    }

    @Test
    void insertUsesOnlyPersistWithoutMergeSaveOrImplicitFlush() {
        var payment = new Payment(mock(Order.class), Instant.parse("2026-10-21T11:00:00.123Z"));
        inserts.insert(payment);
        verify(entityManager).persist(payment);
        verify(entityManager, never()).merge(any());
        verifyNoMoreInteractions(entityManager);
    }

    @Test
    void refreshFailurePropagatesWithoutReadingTheRootOrContinuing() {
        var root = mock(Order.class);
        var failure = new IllegalStateException("controlled refresh failure");
        when(entityManager.find(Order.class, 42L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(root);
        doThrow(failure).when(entityManager).refresh(root, LockModeType.PESSIMISTIC_WRITE);
        assertThatThrownBy(() -> locks.findPaymentOrderForUpdate(42L)).isSameAs(failure);
        verify(entityManager).find(Order.class, 42L, LockModeType.PESSIMISTIC_WRITE);
        verify(entityManager).refresh(root, LockModeType.PESSIMISTIC_WRITE);
        verifyNoMoreInteractions(entityManager);
        verifyNoInteractions(root);
    }

    @Test
    void persistFailurePropagatesWithoutRetryMergeOrFallbackQueries() {
        var payment = new Payment(mock(Order.class), Instant.parse("2026-10-21T11:00:00.123Z"));
        var failure = new IllegalStateException("controlled persistence failure");
        doThrow(failure).when(entityManager).persist(payment);
        assertThatThrownBy(() -> inserts.insert(payment)).isSameAs(failure);
        verify(entityManager).persist(payment);
        verifyNoMoreInteractions(entityManager);
    }
}
