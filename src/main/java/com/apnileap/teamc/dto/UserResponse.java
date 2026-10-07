package com.apnileap.teamc.dto;

import com.apnileap.teamc.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID userId, String email, String displayName, String role, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getUserId(), u.getEmail(), u.getDisplayName(), u.getRole().name(), u.getCreatedAt());
    }
}
