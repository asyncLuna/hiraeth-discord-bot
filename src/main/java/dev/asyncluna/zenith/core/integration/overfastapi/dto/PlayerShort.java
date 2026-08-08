package dev.asyncluna.zenith.core.integration.overfastapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerShort(
    @JsonProperty("player_id") String playerId,
    String name,
    String avatar,
    String namecard,
    String title,
    @JsonProperty("career_url") String careerUrl,
    @JsonProperty("blizzard_id") String blizzardId,
    @JsonProperty("last_updated_at") Long lastUpdatedAt,
    @JsonProperty("is_public") Boolean isPublic) {}
