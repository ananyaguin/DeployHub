package com.cloudflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import com.cloudflow.dto.request.CreateUserRequest;
import com.cloudflow.dto.request.LoginRequest;
import com.cloudflow.dto.request.PageQuery;
import com.cloudflow.dto.request.PasswordResetConfirmRequest;
import com.cloudflow.dto.request.PasswordResetRequest;
import com.cloudflow.dto.request.RegisterRequest;
import com.cloudflow.dto.request.VerifyEmailRequest;
import com.cloudflow.dto.response.LoginResponse;
import com.cloudflow.dto.response.PageResponse;
import com.cloudflow.dto.response.RegisterResponse;
import com.cloudflow.dto.response.TenantResponse;
import com.cloudflow.dto.response.UserResponse;
import com.cloudflow.entity.EmailVerificationToken;
import com.cloudflow.entity.PasswordResetToken;
import com.cloudflow.entity.Tenant;
import com.cloudflow.entity.User;
import com.cloudflow.entity.UserRole;
import com.cloudflow.exception.AuthenticationFailedException;
import com.cloudflow.exception.ConflictException;
import com.cloudflow.exception.EmailNotVerifiedException;
import com.cloudflow.exception.ForbiddenException;
import com.cloudflow.exception.TokenRejectedException;
import com.cloudflow.exception.UnauthorizedException;
import com.cloudflow.repository.EmailVerificationTokenRepository;
import com.cloudflow.repository.PasswordResetTokenRepository;
import com.cloudflow.repository.TenantRepository;
import com.cloudflow.repository.UserRepository;
import com.cloudflow.security.AccountPolicy;
import com.cloudflow.security.AuthenticatedUser;
import com.cloudflow.security.CurrentUserAccessor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantRepository tenants;
    private final UserRepository users;
    private final EmailVerificationTokenRepository verifications;
    private final PasswordResetTokenRepository resets;
    private final PasswordEncoder passwords;
    private final AccountPolicy policy;
    private final AccessTokenService tokens;
    private final CurrentUserAccessor currentUser;
    private final AccountMail mail;
    private final Clock clock;
    private final String timingPad;

    public AccountService(
            TenantRepository tenants,
            UserRepository users,
            EmailVerificationTokenRepository verifications,
            PasswordResetTokenRepository resets,
            PasswordEncoder passwords,
            AccountPolicy policy,
            AccessTokenService tokens,
            CurrentUserAccessor currentUser,
            AccountMail mail,
            Clock clock) {
        this.tenants = tenants;
        this.users = users;
        this.verifications = verifications;
        this.resets = resets;
        this.passwords = passwords;
        this.policy = policy;
        this.tokens = tokens;
        this.currentUser = currentUser;
        this.mail = mail;
        this.clock = clock;
        this.timingPad = passwords.encode("timing-pad");
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String slug = request.tenantSlug().trim();
        if (tenants.findBySlug(slug).isPresent()) {
            throw new ConflictException();
        }
        Tenant tenant = new Tenant();
        tenant.setName(request.tenantName().trim());
        tenant.setSlug(slug);
        tenant.assignIdentity();
        tenant = tenants.save(tenant);

        User user = newUser(tenant, email(request.email()), request.password(), request.displayName(), UserRole.OWNER);
        sendVerification(user);
        return new RegisterResponse(userResponse(user), tenantResponse(tenant));
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        Instant now = clock.instant();
        String hash = hash(request.token());
        if (verifications.consume(hash, now) != 1) {
            throw new TokenRejectedException();
        }
        User user = verifications.findByTokenHash(hash).orElseThrow(TokenRejectedException::new).getUser();
        if (user.getId() != null) {
            verifications.retireOthers(user.getId(), hash, now);
        }
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
            users.save(user);
        }
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        Tenant tenant = tenants.findBySlug(request.tenantSlug().trim()).orElse(null);
        User user = tenant == null
                ? null
                : users.findByTenant_IdAndEmail(tenant.getId(), email(request.email())).orElse(null);
        if (user == null) {
            return;
        }
        String raw = rawToken();
        mail.sendPasswordReset(user.getEmail(), raw);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(clock.instant().plus(mail.resetTtl()));
        resets.save(token);
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        Instant now = clock.instant();
        String hash = hash(request.token());
        if (resets.consume(hash, now) != 1) {
            throw new TokenRejectedException();
        }
        User user = resets.findByTokenHash(hash).orElseThrow(TokenRejectedException::new).getUser();
        if (user.getId() != null) {
            resets.retireOthers(user.getId(), hash, now);
        }
        user.setPasswordHash(passwords.encode(request.password()));
        users.save(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Tenant tenant = tenants.findBySlug(request.tenantSlug().trim()).orElse(null);
        User user = tenant == null
                ? null
                : users.findByTenant_IdAndEmail(tenant.getId(), email(request.email()))
                        .orElse(null);
        String hash = user == null ? timingPad : user.getPasswordHash();
        if (user == null || !passwords.matches(request.password(), hash)) {
            throw new AuthenticationFailedException();
        }
        if (!policy.canAuthenticate(user)) {
            throw new EmailNotVerifiedException();
        }
        return tokens.issue(user);
    }

    @Transactional
    public UserResponse addUser(CreateUserRequest request) {
        AuthenticatedUser caller = currentUser.require();
        if (!canGrant(caller.role(), request.role())) {
            throw new ForbiddenException();
        }
        Tenant tenant = tenants.findById(caller.tenantKey()).orElseThrow(UnauthorizedException::new);
        if (users.findByTenant_IdAndEmail(tenant.getId(), email(request.email())).isPresent()) {
            throw new ConflictException();
        }
        User user = newUser(tenant, email(request.email()), request.password(), request.displayName(), request.role());
        sendVerification(user);
        return userResponse(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(PageQuery page) {
        AuthenticatedUser caller = currentUser.require();
        Page<User> results = users.findByTenant_Id(caller.tenantKey(), PageRequest.of(page.page(), page.size()));
        return new PageResponse<>(
                results.stream().map(AccountService::userResponse).toList(),
                page.page(),
                page.size(),
                results.getTotalElements());
    }

    private void sendVerification(User user) {
        String raw = rawToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(clock.instant().plus(mail.verificationTtl()));
        verifications.save(token);
        mail.sendVerification(user.getEmail(), raw);
    }

    private static String rawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private User newUser(Tenant tenant, String email, String password, String displayName, UserRole role) {
        User user = new User();
        user.setTenant(tenant);
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(password));
        user.setDisplayName(displayName.trim());
        user.setRole(role);
        user.assignIdentity();
        return users.save(user);
    }

    private static boolean canGrant(UserRole caller, UserRole requested) {
        return switch (requested) {
            case OWNER -> false;
            case ADMIN -> caller == UserRole.OWNER;
            case MEMBER -> caller == UserRole.OWNER || caller == UserRole.ADMIN;
        };
    }

    private static String email(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static UserResponse userResponse(User user) {
        return new UserResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getEmailVerifiedAt() != null);
    }

    private static TenantResponse tenantResponse(Tenant tenant) {
        return new TenantResponse(tenant.getPublicId(), tenant.getName(), tenant.getSlug());
    }
}
