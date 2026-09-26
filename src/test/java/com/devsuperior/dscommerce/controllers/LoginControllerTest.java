package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.SecurityConfig;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.services.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class LoginControllerTest {

    private static final String EXACT_NAME = "  Demo.User  ";
    private static final String EXACT_PASSWORD = "  S3cret-Pass  ";
    private static final String ACCESS_TOKEN = "signed.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationService authenticationService;

    @Test
    void postLoginShouldAuthenticateExactCredentialsAndReturnOnlyBearerToken() throws Exception {
        when(authenticationService.login(EXACT_NAME, EXACT_PASSWORD))
                .thenReturn(new TokenResponseDTO(ACCESS_TOKEN, "Bearer"));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Demo.User  ","password":"  S3cret-Pass  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("accessToken", "tokenType")))
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.jwtSecret").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.signingKey").doesNotExist())
                .andExpect(jsonPath("$.key").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.permissions").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(authenticationService, times(1)).login(EXACT_NAME, EXACT_PASSWORD);
        verifyNoMoreInteractions(authenticationService);
    }
}
