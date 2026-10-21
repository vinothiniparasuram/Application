package com.lifeorganizer.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank(message = "Name is required")
		@Size(max = 100, message = "Name must be at most 100 characters")
		String name,

		@NotBlank(message = "Email is required")
		@Email(message = "Email must be a valid email address")
		@Size(max = 254, message = "Email must be at most 254 characters")
		String email,

		// BCrypt only uses the first 72 bytes; RegistrationService also checks the byte length.
		@NotBlank(message = "Password is required")
		@Size(min = 4, max = 72, message = "Password must be between 4 and 72 characters")
		String password) {

	@Override
	public String toString() {
		return "RegisterRequest[name=" + name + ", email=" + email + ", password=***]";
	}

}
