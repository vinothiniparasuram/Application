package com.lifeorganizer.auth;

import java.time.Instant;

import com.lifeorganizer.user.UserResponse;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {

}
