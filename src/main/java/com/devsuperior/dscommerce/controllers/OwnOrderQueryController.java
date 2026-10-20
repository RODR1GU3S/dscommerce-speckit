package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.OwnOrderPageRequestDTO;
import com.devsuperior.dscommerce.dto.OwnOrderSummaryDTO;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.services.OwnOrderQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for the authenticated account's order list. */
@RestController
public class OwnOrderQueryController {

    private final OwnOrderQueryService service;

    public OwnOrderQueryController(OwnOrderQueryService service) {
        this.service = service;
    }

    @GetMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PageResponseDTO<OwnOrderSummaryDTO>> list(@Valid OwnOrderPageRequestDTO request,
                                                                   Authentication authentication,
                                                                   HttpServletRequest servletRequest) {
        var response = service.list(request, authentication);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("HEAD".equals(servletRequest.getMethod()) ? null : response);
    }
}
