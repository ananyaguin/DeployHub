package com.cloudflow.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.cloudflow.entity.User;
import com.cloudflow.entity.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AuthenticatedUser implements UserDetails {

    private final UUID userId;
    private final UUID tenantId;
    private final Long tenantKey;
    private final UserRole role;
    private final String email;

    public AuthenticatedUser(UUID userId, UUID tenantId, Long tenantKey, UserRole role, String email) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.tenantKey = tenantKey;
        this.role = role;
        this.email = email;
    }

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(
                user.getPublicId(),
                user.getTenant().getPublicId(),
                user.getTenant().getId(),
                user.getRole(),
                user.getEmail());
    }

    public UUID userId() {
        return userId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public Long tenantKey() {
        return tenantKey;
    }

    public UserRole role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
