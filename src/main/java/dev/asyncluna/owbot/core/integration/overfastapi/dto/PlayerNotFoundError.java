package dev.asyncluna.owbot.core.integration.overfastapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerNotFoundError(
    String error,
    @JsonProperty("retry_after") int retryAfter,
    @JsonProperty("next_check_at") long nextCheckAt,
    @JsonProperty("check_count") int checkCount) {}
