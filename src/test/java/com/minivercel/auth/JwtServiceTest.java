package com.minivercel.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;

import com.minivercel.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

	private AppProperties props;
	private JwtService jwtService;

	@BeforeEach
	void setUp() {
		props = new AppProperties(
				new AppProperties.Jwt("super-secret-key-that-is-at-least-32-bytes-long!", Duration.ofHours(1)),
				null, null, null, null);
		jwtService = new JwtService(props);
	}

	@Test
	void issueAndVerifySuccess() {
		String token = jwtService.issue("user-123", "user@example.com");
		assertThat(token).isNotBlank();

		Optional<String> verifiedUserId = jwtService.verify(token);
		assertThat(verifiedUserId).isPresent().contains("user-123");
	}

	@Test
	void verifyInvalidTokenReturnsEmpty() {
		Optional<String> result = jwtService.verify("invalid.jwt.token");
		assertThat(result).isEmpty();
	}

	@Test
	void verifyTamperedTokenReturnsEmpty() {
		String token = jwtService.issue("user-123", "user@example.com");
		String tampered = token.substring(0, token.length() - 4) + "abcd";

		Optional<String> result = jwtService.verify(tampered);
		assertThat(result).isEmpty();
	}

	@Test
	void rejectShortSecretOnCreation() {
		AppProperties shortSecretProps = new AppProperties(
				new AppProperties.Jwt("too-short", Duration.ofHours(1)),
				null, null, null, null);

		assertThatThrownBy(() -> new JwtService(shortSecretProps))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("must be at least 32 bytes");
	}
}
