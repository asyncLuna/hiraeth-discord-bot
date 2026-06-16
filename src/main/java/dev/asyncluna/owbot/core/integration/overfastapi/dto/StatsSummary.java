package dev.asyncluna.owbot.core.integration.overfastapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StatsSummary(
    @JsonProperty("games_played") Integer gamesPlayed,
    @JsonProperty("games_won") Integer gamesWon,
    @JsonProperty("games_lost") Integer gamesLost,
    @JsonProperty("time_played") Integer timePlayed,
    Double winrate,
    Double kda,
    TotalStatsSummary total,
    AverageStatsSummary average) {}
