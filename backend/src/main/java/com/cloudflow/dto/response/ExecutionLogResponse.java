package com.cloudflow.dto.response;

import com.cloudflow.entity.LogLevel;

public record ExecutionLogResponse(int sequenceNumber, LogLevel level, String message) {}
