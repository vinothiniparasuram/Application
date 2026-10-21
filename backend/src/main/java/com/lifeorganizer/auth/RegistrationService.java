package com.lifeorganizer.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.lifeorganizer.user.User;
import com.lifeorganizer.user.UserRepository;
import com.lifeorganizer.user.UserResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

	static final int MAX_PASSWORD_BYTES = 72;

	private static final String DEFAULT_TIMEZONE = "UTC";

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokenService;

	public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			TokenService tokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw new PasswordTooLongException();
		}
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyRegisteredException();
		}
		User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()),
				DEFAULT_TIMEZONE);
		try {
			user = userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Two requests with the same email raced past the exists check; the unique constraint caught it.
			throw new EmailAlreadyRegisteredException();
		}
		TokenService.IssuedToken token = tokenService.issue(user);
		return new AuthResponse(token.value(), "Bearer", token.expiresAt(), UserResponse.from(user));
	}

}
