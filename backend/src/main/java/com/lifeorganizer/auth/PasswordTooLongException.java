package com.lifeorganizer.auth;

public class PasswordTooLongException extends RuntimeException {

	public PasswordTooLongException() {
		super("Password must be at most 72 bytes");
	}

}
