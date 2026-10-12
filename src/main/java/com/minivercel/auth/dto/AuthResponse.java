package com.minivercel.auth.dto;

/** Never carries the password hash — that is the point of having a DTO here. */
public record AuthResponse(String token, String tokenType, long expiresInSeconds, String userId,
		String email) {

	public static AuthResponse bearer(String token, long expiresInSeconds, String userId,
			String email) {
		return new AuthResponse(token, "Bearer", expiresInSeconds, userId, email);
	}
}
