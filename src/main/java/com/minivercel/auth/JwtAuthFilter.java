package com.minivercel.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer <token>} and, if it verifies, puts the userId into the
 * security context as the principal. Requests without a valid token simply pass through
 * unauthenticated; the filter chain decides what that's allowed to reach.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

	private static final String PREFIX = "Bearer ";

	private final JwtService jwtService;

	public JwtAuthFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response, @NonNull FilterChain chain)
			throws ServletException, IOException {

		String header = request.getHeader("Authorization");

		if (header != null && header.startsWith(PREFIX)
				&& SecurityContextHolder.getContext().getAuthentication() == null) {

			this.jwtService.verify(header.substring(PREFIX.length()).trim()).ifPresent((userId) -> {
				var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
				auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(auth);
			});
		}

		chain.doFilter(request, response);
	}
}
