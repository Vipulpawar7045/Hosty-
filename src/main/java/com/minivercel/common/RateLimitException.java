package com.minivercel.common;

/**
 * Thrown when a rate limit or quota is exceeded.
 */
public class RateLimitException extends RuntimeException {

	public RateLimitException(String message) {
		super(message);
	}
}
