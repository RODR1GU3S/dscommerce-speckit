package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.LoginRequestDTO;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.services.AuthenticationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    private final AuthenticationService authenticationService;

    public LoginController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public TokenResponseDTO login(@RequestBody LoginRequestDTO dto) {
        return authenticationService.login(dto.name(), dto.password());
    }
}
