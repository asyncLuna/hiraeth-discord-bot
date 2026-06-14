package dev.asyncluna.owbot.core.integration.overfastapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerCompetitiveRank(
    String division,
    int tier,
    @JsonProperty("role_icon") String roleIcon,
    @JsonProperty("rank_icon") String rankIcon,
    @JsonProperty("tier_icon") String tierIcon) {}
