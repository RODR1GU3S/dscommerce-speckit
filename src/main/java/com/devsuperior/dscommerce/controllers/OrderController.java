package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.dto.OrderResponseDTO;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponseDTO> register(@Valid @RequestBody OrderRequestDTO request,
                                                    @AuthenticationPrincipal AuthenticatedAccountPrincipal principal) {
        var response = orderService.register(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
