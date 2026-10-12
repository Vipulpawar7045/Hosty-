package com.minivercel.user;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.minivercel.auth.JwtService;
import com.minivercel.auth.dto.AuthResponse;
import com.minivercel.auth.dto.LoginRequest;
import com.minivercel.auth.dto.RegisterRequest;
import com.minivercel.common.BadRequestException;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserRepository repository;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	public UserService(UserRepository repository, PasswordEncoder passwordEncoder,
			JwtService jwtService) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public AuthResponse register(RegisterRequest request) {
		String email = normalise(request.email());

		var user = new User();
		user.setUserId(UUID.randomUUID().toString());
		user.setEmail(email);
		user.setPasswordHash(this.passwordEncoder.encode(request.password()));
		user.setCreatedAt(Instant.now().toString());

		if (!this.repository.saveIfEmailAvailable(user)) {
			throw new BadRequestException("An account with that email already exists");
		}

		return token(user);
	}

	public AuthResponse login(LoginRequest request) {
		var user = this.repository.findByEmail(normalise(request.email()))
				.orElseThrow(() -> new BadCredentialsException("No such user"));

		if (!this.passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new BadCredentialsException("Wrong password");
		}

		return token(user);
	}

	private AuthResponse token(User user) {
		String jwt = this.jwtService.issue(user.getUserId(), user.getEmail());
		return AuthResponse.bearer(jwt, this.jwtService.expirySeconds(), user.getUserId(),
				user.getEmail());
	}

	/** Emails are matched case-insensitively, so they are stored lowercased. */
	private static String normalise(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
