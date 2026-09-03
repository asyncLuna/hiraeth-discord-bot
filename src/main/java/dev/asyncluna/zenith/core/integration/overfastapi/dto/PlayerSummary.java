package dev.asyncluna.zenith.core.integration.overfastapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerSummary(
        String username,
        String avatar,
        String namecard,
        String title,
        PlayerEndorsement endorsement,
        PlayerCompetitiveRanksContainer competitive,
        @JsonProperty("last_updated_at") Long lastUpdatedAt) {}
