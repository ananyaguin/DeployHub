package com.cloudflow.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PageQuery(@NotNull @Min(0) Integer page, @NotNull @Min(1) @Max(100) Integer size) {}
