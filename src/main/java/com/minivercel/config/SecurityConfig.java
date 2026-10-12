package com.minivercel.config;

import com.minivercel.auth.JwtAuthFilter;
import com.minivercel.common.ApiError;
import tools.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
			ObjectMapper objectMapper) throws Exception {

		http
			// No cookies or sessions are used, so there is no CSRF vector to protect.
			.csrf((csrf) -> csrf.disable())
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests((auth) -> auth
					.requestMatchers("/api/auth/**").permitAll()
					// GitHub webhooks need to be public
					.requestMatchers("/api/webhooks/**").permitAll()
					// Admin endpoints - TODO: add proper authentication
					.requestMatchers("/api/admin/**").permitAll()
					// Deployed sites are public by design — that is the whole product.
					.requestMatchers("/sites/**").permitAll()
					.requestMatchers("/actuator/health").permitAll()
					.anyRequest().authenticated())
			.exceptionHandling((ex) -> ex.authenticationEntryPoint((request, response, authEx) -> {
				response.setStatus(HttpStatus.UNAUTHORIZED.value());
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				objectMapper.writeValue(response.getWriter(),
						ApiError.of("unauthorized", "A valid Bearer token is required"));
			}))
			.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
}
