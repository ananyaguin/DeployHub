package com.cloudflow.controller;

import com.cloudflow.dto.request.CreateUserRequest;
import com.cloudflow.dto.request.PageQuery;
import com.cloudflow.dto.response.PageResponse;
import com.cloudflow.dto.response.UserResponse;
import com.cloudflow.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final AccountService accounts;

    public UserController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/api/v1/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return accounts.addUser(request);
    }

    @GetMapping("/api/v1/users")
    public PageResponse<UserResponse> list(@Valid PageQuery page) {
        return accounts.listUsers(page);
    }
}
