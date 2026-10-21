package com.lifeorganizer.user;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail());
	}

}
