package com.cloudflow.security;

import com.cloudflow.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AccountPolicy {

    private final boolean requireVerifiedEmail;

    public AccountPolicy(SecurityProperties properties) {
        this.requireVerifiedEmail = properties.requireVerifiedEmail();
    }

    // ponytail: emailVerifiedAt is the only blocked state. Check a disabled flag here when the schema has one.
    public boolean canAuthenticate(User user) {
        return !requireVerifiedEmail || user.getEmailVerifiedAt() != null;
    }
}
