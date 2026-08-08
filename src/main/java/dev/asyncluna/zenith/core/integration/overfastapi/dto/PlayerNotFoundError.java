package dev.asyncluna.zenith.core.integration.overfastapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerNotFoundError(
    String error,
    @JsonProperty("retry_after") Integer retryAfter,
    @JsonProperty("next_check_at") Long nextCheckAt,
    @JsonProperty("check_count") Integer checkCount) {}
