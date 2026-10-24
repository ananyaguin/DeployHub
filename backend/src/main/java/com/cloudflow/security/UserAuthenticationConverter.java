package com.cloudflow.security;

import java.util.UUID;

import com.cloudflow.entity.User;
import com.cloudflow.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class UserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;
    private final AccountPolicy policy;

    public UserAuthenticationConverter(UserRepository users, AccountPolicy policy) {
        this.users = users;
        this.policy = policy;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = subject(jwt);
        User user = users.findByPublicId(userId)
                .orElseThrow(() -> new BadCredentialsException("Authentication required"));
        if (!policy.canAuthenticate(user)) {
            throw new BadCredentialsException("Authentication required");
        }
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static UUID subject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new BadCredentialsException("Authentication required");
        }
    }
}
