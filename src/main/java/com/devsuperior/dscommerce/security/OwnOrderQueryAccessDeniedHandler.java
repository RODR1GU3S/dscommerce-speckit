package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.dto.ErrorResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** Query-only permission rejection, without account data or exception diagnostics. */
public class OwnOrderQueryAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper mapper;

    public OwnOrderQueryAccessDeniedHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        var status = HttpStatus.FORBIDDEN;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (!"HEAD".equals(request.getMethod())) {
            mapper.writeValue(response.getOutputStream(), new ErrorResponseDTO(status.value(),
                    status.getReasonPhrase(), "Order query not permitted", request.getRequestURI()));
        }
    }
}
