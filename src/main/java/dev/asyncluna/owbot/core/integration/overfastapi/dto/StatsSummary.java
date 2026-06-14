package dev.asyncluna.owbot.core.integration.overfastapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StatsSummary(
    @JsonProperty("games_played") int gamesPlayed,
    @JsonProperty("games_won") int gamesWon,
    @JsonProperty("games_lost") int gamesLost,
    @JsonProperty("time_played") int timePlayed,
    double winrate,
    double kda,
    TotalStatsSummary total,
    AverageStatsSummary average) {}
