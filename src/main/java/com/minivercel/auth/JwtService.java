package com.minivercel.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import com.minivercel.config.AppProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Service;

/** Signs and verifies the HS256 tokens the API authenticates with. */
@Service
public class JwtService {

	private final SecretKey key;

	private final long expirySeconds;

	public JwtService(AppProperties props) {
		byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < 32) {
			throw new IllegalStateException(
					"minivercel.jwt.secret must be at least 32 bytes for HS256; got " + secret.length);
		}
		this.key = Keys.hmacShaKeyFor(secret);
		this.expirySeconds = props.jwt().expiry().toSeconds();
	}

	public String issue(String userId, String email) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(userId)
				.claim("email", email)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(this.expirySeconds)))
				.signWith(this.key)
				.compact();
	}

	/** @return the subject (userId), or empty if the token is invalid, expired or tampered with */
	public Optional<String> verify(String token) {
		try {
			var claims = Jwts.parser()
					.verifyWith(this.key)
					.build()
					.parseSignedClaims(token)
					.getPayload();

			return Optional.ofNullable(claims.getSubject());
		}
		catch (JwtException | IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	public long expirySeconds() {
		return this.expirySeconds;
	}
}
