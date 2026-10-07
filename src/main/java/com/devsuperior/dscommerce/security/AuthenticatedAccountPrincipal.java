package com.devsuperior.dscommerce.security;

/** Transient authenticated identity; persistence and permissions stay outside this value. */
public record AuthenticatedAccountPrincipal(Long accountId) {
}
