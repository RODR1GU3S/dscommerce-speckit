package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.PaymentConfirmationDTO;
import com.devsuperior.dscommerce.dto.PaymentRegistrationRequestDTO;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.entities.Payment;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import com.devsuperior.dscommerce.repositories.PaymentRepository;
import com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException;
import jakarta.persistence.EntityManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import static com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException.Reason.*;

/** Records the declared settlement through the caller's locked order transaction. */
@Service
public class PaymentRegistrationService {
    private static final Instant MIN_OCCURRENCE = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_OCCURRENCE = Instant.parse("9999-12-31T23:59:59.999Z");

    private final Clock clock;
    private final PaymentAccessService access;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final EntityManager entityManager;

    public PaymentRegistrationService(Clock clock, PaymentAccessService access, OrderRepository orders,
                                      PaymentRepository payments, EntityManager entityManager) {
        this.clock = clock;
        this.access = access;
        this.orders = orders;
        this.payments = payments;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public PaymentConfirmationDTO register(long orderId, PaymentRegistrationRequestDTO request,
                                            Authentication authentication) {
        // Keep native precision and this same value across every lookup and lock wait.
        var reference = clock.instant();
        access.requireAdmin(authentication);
        var order = orders.findPaymentOrderForUpdate(orderId)
                .orElseThrow(() -> new PaymentRegistrationException(ORDER_NOT_FOUND));
        access.requireAdmin(authentication);
        if (payments.findById(orderId).isPresent()) {
            throw new PaymentRegistrationException(ALREADY_REGISTERED);
        }
        if (!Long.valueOf(orderId).equals(order.getId()) || order.getCreatedAt() == null
                || order.getStatus() == null) {
            throw new PaymentRegistrationException(PROCESSING_FAILURE);
        }
        if (order.getStatus() != OrderStatus.WAITING_PAYMENT) {
            throw new PaymentRegistrationException(INCOMPATIBLE_STATE);
        }
        var occurredAt = request == null ? null : request.occurredAt();
        if (occurredAt == null || occurredAt.getNano() % 1_000_000 != 0
                || occurredAt.isBefore(MIN_OCCURRENCE) || occurredAt.isAfter(MAX_OCCURRENCE)
                || occurredAt.isBefore(order.getCreatedAt()) || occurredAt.isAfter(reference)) {
            throw new PaymentRegistrationException(INVALID_INPUT);
        }
        validateFullHistoricalAmount(orders.findPaymentItemsByOrderId(orderId));
        var payment = new Payment(order, occurredAt);
        payments.insert(payment);
        order.markPaid();
        entityManager.flush();
        return new PaymentConfirmationDTO(order.getId(), payment.getOccurredAt(), order.getStatus());
    }

    /** Derive the full sum from scalar purchase rows; Payment deliberately has no amount column. */
    private static void validateFullHistoricalAmount(List<OrderRepository.PaymentItemView> items) {
        if (items == null || items.isEmpty()) { throw new PaymentRegistrationException(PROCESSING_FAILURE); }
        var products = new HashSet<Long>();
        var total = BigDecimal.ZERO;
        for (int position = 0; position < items.size(); position++) {
            var item = items.get(position);
            if (item == null || item.getProductId() == null || item.getProductId() <= 0
                    || !products.add(item.getProductId()) || !Integer.valueOf(position).equals(item.getPosition())
                    || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new PaymentRegistrationException(PROCESSING_FAILURE);
            }
            var price = exactMoney(item.getUnitPrice());
            var subtotal = exactMoney(price.multiply(BigDecimal.valueOf(item.getQuantity().longValue())));
            total = total.add(subtotal);
        }
        exactMoney(total);
    }

    private static BigDecimal exactMoney(BigDecimal value) {
        if (value == null || value.signum() <= 0) { throw new PaymentRegistrationException(PROCESSING_FAILURE); }
        final BigDecimal exact;
        try {
            exact = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException cause) {
            throw new PaymentRegistrationException(PROCESSING_FAILURE, cause);
        }
        if (exact.precision() > 19) { throw new PaymentRegistrationException(PROCESSING_FAILURE); }
        return exact;
    }
}
