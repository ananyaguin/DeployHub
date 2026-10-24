package com.cloudflow.dto.response;

import java.util.UUID;

public record TenantResponse(UUID publicId, String name, String slug) {}
