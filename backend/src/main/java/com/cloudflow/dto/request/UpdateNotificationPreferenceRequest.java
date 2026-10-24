package com.cloudflow.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequest(@NotNull Boolean emailEnabled) {}
