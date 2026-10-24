package com.cloudflow.dto.response;

import java.util.UUID;

import com.cloudflow.entity.UserRole;

public record UserResponse(
        UUID publicId, String email, String displayName, UserRole role, boolean emailVerified) {}
