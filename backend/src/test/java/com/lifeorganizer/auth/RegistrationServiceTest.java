package com.lifeorganizer.auth;

import java.time.Instant;

import com.lifeorganizer.user.User;
import com.lifeorganizer.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private TokenService tokenService;

	@InjectMocks
	private RegistrationService registrationService;

	@Test
	void savesUserWithNormalizedEmailAndHashedPassword() {
		given(userRepository.existsByEmail("vino@example.com")).willReturn(false);
		given(passwordEncoder.encode("secret123")).willReturn("hashed");
		given(userRepository.saveAndFlush(any(User.class))).willAnswer((invocation) -> invocation.getArgument(0));
		given(tokenService.issue(any(User.class))).willReturn(new TokenService.IssuedToken("jwt", Instant.EPOCH));

		AuthResponse response = registrationService
			.register(new RegisterRequest("  Vino  ", "  Vino@Example.COM ", "secret123"));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getName()).isEqualTo("Vino");
		assertThat(saved.getValue().getEmail()).isEqualTo("vino@example.com");
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
		assertThat(response.accessToken()).isEqualTo("jwt");
		assertThat(response.tokenType()).isEqualTo("Bearer");
	}

	@Test
	void rejectsDuplicateEmailWithoutSaving() {
		given(userRepository.existsByEmail("taken@example.com")).willReturn(true);

		assertThatExceptionOfType(EmailAlreadyRegisteredException.class)
			.isThrownBy(() -> registrationService.register(new RegisterRequest("A", "Taken@example.com", "secret123")));
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void rejectsDuplicateEmailWhenConcurrentInsertHitsUniqueConstraint() {
		given(userRepository.existsByEmail("race@example.com")).willReturn(false);
		given(passwordEncoder.encode("secret123")).willReturn("hashed");
		given(userRepository.saveAndFlush(any(User.class))).willThrow(new DataIntegrityViolationException("uk_users_email"));

		assertThatExceptionOfType(EmailAlreadyRegisteredException.class)
			.isThrownBy(() -> registrationService.register(new RegisterRequest("A", "race@example.com", "secret123")));
	}

	@Test
	void rejectsPasswordLongerThan72Bytes() {
		// 40 characters but 80 bytes in UTF-8; bcrypt would silently ignore everything past byte 72
		String password = "é".repeat(40);

		assertThatExceptionOfType(PasswordTooLongException.class)
			.isThrownBy(() -> registrationService.register(new RegisterRequest("A", "a@example.com", password)));
		verify(passwordEncoder, never()).encode(any());
	}

}
