package com.apnileap.teamc.dto;

import com.apnileap.teamc.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String displayName,
        @NotNull UserRole role
) {}
