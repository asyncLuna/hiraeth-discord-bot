package dev.asyncluna.hiraeth.core.integration.overfastapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoleRank(
    String division,
    int tier,
    @JsonProperty("role_icon") String roleIcon,
    @JsonProperty("rank_icon") String rankIcon,
    @JsonProperty("tier_icon") String tierIcon) {}
