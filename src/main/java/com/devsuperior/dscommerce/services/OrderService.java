package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.OrderItemResponseDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.dto.OrderResponseDTO;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.entities.OrderItem;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;

/** Registers one complete historical aggregate and maps its confirmation in the transaction. */
@Service
public class OrderService {

    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public OrderService(ProductRepository products, OrderRepository orders, UserAccountRepository accounts, Clock clock) {
        this.products = products;
        this.orders = orders;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public OrderResponseDTO register(OrderRequestDTO request, AuthenticatedAccountPrincipal principal) {
        if (principal == null || principal.accountId() == null || principal.accountId() <= 0) {
            throw new BadCredentialsException("Authentication required or invalid");
        }
        var buyer = accounts.findByIdWithRoles(principal.accountId())
                .orElseThrow(() -> new BadCredentialsException("Authentication required or invalid"));
        if (!buyer.getRoles().contains(UserRole.CLIENT) && !buyer.getRoles().contains(UserRole.ADMIN)) {
            throw new AccessDeniedException("Order registration not permitted");
        }

        var ids = request.items().stream().map(line -> line.productId()).toList();
        var byId = new HashMap<Long, Product>();
        products.findAllById(ids).forEach(product -> byId.put(product.getId(), product));

        var items = new ArrayList<OrderItem>(request.items().size());
        var total = BigDecimal.ZERO;
        for (var line : request.items()) {
            var product = byId.get(line.productId());
            if (product == null) {
                throw new ProductNotFoundException("Product not found");
            }
            var price = exactMoney(product.getPrice());
            var item = new OrderItem(product, line.quantity(), price);
            var subtotal = exactMoney(item.getSubtotal());
            total = exactMoney(total.add(subtotal));
            items.add(item);
        }

        var createdAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        var order = new Order(buyer, createdAt, items);
        var saved = orders.saveAndFlush(order);
        // Flush executes SQL; the surrounding Spring interceptor still owns transaction completion.
        var responseItems = saved.getItems().stream()
                .map(item -> new OrderItemResponseDTO(item.getProduct().getId(), item.getQuantity(),
                        item.getUnitPrice().toPlainString(), item.getSubtotal().toPlainString()))
                .toList();
        return new OrderResponseDTO(saved.getId(), saved.getBuyer().getId(), saved.getCreatedAt(),
                saved.getStatus(), responseItems, saved.getTotal().toPlainString());
    }

    // Numerical preconditions only; domain rejection reasons and HTTP translation belong to later tasks.
    private static BigDecimal exactMoney(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Order value must be positive");
        }
        var normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.precision() > 19) {
            throw new IllegalArgumentException("Order value exceeds decimal19,2");
        }
        return normalized;
    }
}
