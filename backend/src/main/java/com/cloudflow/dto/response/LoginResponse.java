package com.cloudflow.dto.response;

public record LoginResponse(String accessToken, long expiresIn, String tokenType) {}
