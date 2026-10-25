package com.cloudflow.controller;

import com.cloudflow.dto.request.LoginRequest;
import com.cloudflow.dto.request.PasswordResetConfirmRequest;
import com.cloudflow.dto.request.PasswordResetRequest;
import com.cloudflow.dto.request.RegisterRequest;
import com.cloudflow.dto.request.VerifyEmailRequest;
import com.cloudflow.dto.response.LoginResponse;
import com.cloudflow.dto.response.RegisterResponse;
import com.cloudflow.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return accounts.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return accounts.login(request);
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        accounts.verifyEmail(request);
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        accounts.requestPasswordReset(request);
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        accounts.confirmPasswordReset(request);
    }
}
