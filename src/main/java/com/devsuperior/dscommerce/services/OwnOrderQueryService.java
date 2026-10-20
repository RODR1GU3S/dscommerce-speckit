package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.OwnOrderPageRequestDTO;
import com.devsuperior.dscommerce.dto.OwnOrderSummaryDTO;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Own-order reads and complete scalar mapping within the same read transaction. */
@Service
public class OwnOrderQueryService {

    private final OwnOrderQueryAccessService access;
    private final OrderRepository orders;

    public OwnOrderQueryService(OwnOrderQueryAccessService access, OrderRepository orders) {
        this.access = access;
        this.orders = orders;
    }

    /** Pagination has already passed the dedicated MVC resolver and Bean Validation. */
    @Transactional(readOnly = true)
    public PageResponseDTO<OwnOrderSummaryDTO> list(OwnOrderPageRequestDTO request, Authentication authentication) {
        long ownerId = access.requireAccountId(authentication);
        int page = request.getPage();
        int size = request.getSize();
        var selected = orders.findOwnOrderIds(ownerId, PageRequest.of(page, size));
        long total = selected.getTotalElements();
        if (total < 0) { throw inconsistentRead(); }
        long pages = total / size + (total % size == 0 ? 0 : 1);
        if (pages > Integer.MAX_VALUE) { throw inconsistentRead(); }
        int totalPages = Math.toIntExact(pages);
        var ids = selected.getContent();
        if (ids.isEmpty()) {
            return new PageResponseDTO<>(List.of(), page, size, total, totalPages);
        }
        var uniqueIds = new HashSet<Long>();
        for (var id : ids) {
            if (id == null || id <= 0 || !uniqueIds.add(id)) { throw inconsistentRead(); }
        }
        var byId = new HashMap<Long, Order>();
        for (var order : orders.findOwnOrdersWithItemsByIds(ownerId, ids)) {
            if (order == null || !uniqueIds.contains(order.getId())
                    || order.getBuyer() == null || !Long.valueOf(ownerId).equals(order.getBuyer().getId())
                    || byId.putIfAbsent(order.getId(), order) != null) {
                throw inconsistentRead();
            }
        }
        if (byId.size() != ids.size()) { throw inconsistentRead(); }
        var content = ids.stream().map(id -> summary(byId.get(id))).toList();
        return new PageResponseDTO<>(content, page, size, total, totalPages);
    }

    private static OwnOrderSummaryDTO summary(Order order) {
        if (order.getCreatedAt() == null || order.getStatus() == null || order.getItems().isEmpty()) {
            throw inconsistentRead();
        }
        // Validate recorded lines only. No current product price or write-time product validation.
        for (int position = 0; position < order.getItems().size(); position++) {
            var item = order.getItems().get(position);
            if (item == null || item.getQuantity() == null || item.getQuantity() <= 0
                    || !Integer.valueOf(position).equals(item.getPosition())
                    || item.getOrder() == null || !order.getId().equals(item.getOrder().getId())
                    || item.getProduct() == null || item.getProduct().getId() == null
                    || item.getProduct().getId() <= 0) {
                throw inconsistentRead();
            }
            exactMoney(item.getUnitPrice());
            exactMoney(item.getSubtotal());
        }
        return new OwnOrderSummaryDTO(order.getId(), order.getCreatedAt(), order.getStatus(),
                exactMoney(order.getTotal()).toPlainString());
    }

    private static BigDecimal exactMoney(BigDecimal value) {
        if (value == null || value.signum() <= 0) { throw inconsistentRead(); }
        final BigDecimal exact;
        try {
            exact = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException cause) {
            throw new IllegalStateException("Inconsistent own-order read", cause);
        }
        if (exact.precision() > 19) { throw inconsistentRead(); }
        return exact;
    }

    private static IllegalStateException inconsistentRead() {
        return new IllegalStateException("Inconsistent own-order read");
    }
}
