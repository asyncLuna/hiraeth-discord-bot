package dev.asyncluna.hiraeth.core.integration.overfastapi.dto;

public record PlayerCompetitiveRank(
    Integer season, RoleRank tank, RoleRank damage, RoleRank support, RoleRank open) {}
