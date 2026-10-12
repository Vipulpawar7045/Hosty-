package com.minivercel.common;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ApiError> notFound(NotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiError.of("not_found", ex.getMessage()));
	}

	@ExceptionHandler(BadRequestException.class)
	ResponseEntity<ApiError> badRequest(BadRequestException ex) {
		return ResponseEntity.badRequest().body(ApiError.of("bad_request", ex.getMessage()));
	}

	@ExceptionHandler(BadCredentialsException.class)
	ResponseEntity<ApiError> badCredentials(BadCredentialsException ex) {
		// Deliberately vague: don't reveal whether the email exists.
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ApiError.of("unauthorized", "Invalid email or password"));
	}

	@ExceptionHandler(RateLimitException.class)
	ResponseEntity<ApiError> rateLimitExceeded(RateLimitException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(ApiError.of("rate_limit_exceeded", ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
		List<String> details = ex.getBindingResult().getFieldErrors().stream()
				.map(err -> err.getField() + ": " + err.getDefaultMessage())
				.toList();

		return ResponseEntity.badRequest()
				.body(ApiError.of("validation_failed", "Request body is invalid", details));
	}
}
