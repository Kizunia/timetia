package com.kibootia.backend.platform.tenancy;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the caller's tenant for the duration of an authenticated API request, so that Hibernate filters every query by
 * it (ADR 0028). It runs after bearer authentication; a request that did not authenticate never reaches a controller
 * and gets no tenant.
 */
public final class TenantContextFilter extends OncePerRequestFilter {

	private final TenantResolver resolver;

	public TenantContextFilter(TenantResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			chain.doFilter(request, response);
			return;
		}
		try (TenantContext.Scope scope = TenantContext.bind(this.resolver.resolve(authentication))) {
			chain.doFilter(request, response);
		}
	}

}
