package com.minivercel.common;

import java.time.Instant;
import java.util.List;

/** Uniform error body for every failure the API returns. */
public record ApiError(String error, String message, List<String> details, String timestamp) {

	public static ApiError of(String error, String message) {
		return new ApiError(error, message, List.of(), Instant.now().toString());
	}

	public static ApiError of(String error, String message, List<String> details) {
		return new ApiError(error, message, details, Instant.now().toString());
	}
}
