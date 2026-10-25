package com.cloudflow.worker;

import java.util.UUID;

public record ClaimedTask(UUID publicId, String taskType, int attempt) {}
