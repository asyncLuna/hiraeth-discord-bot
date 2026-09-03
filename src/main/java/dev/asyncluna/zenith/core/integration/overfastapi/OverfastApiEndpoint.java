package dev.asyncluna.zenith.core.integration.overfastapi;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum OverfastApiEndpoint {
    // Heroes
    GET_A_LIST_OF_HEROES("/heroes", Duration.ofDays(1).getSeconds()),
    GET_HERO_STATS("/heroes/stats", Duration.ofHours(1).getSeconds()),
    GET_HERO_DATA("/heroes/%s", Duration.ofDays(1).getSeconds()),
    GET_A_LIST_OF_ROLES("/roles", Duration.ofDays(1).getSeconds()),

    // Gamemodes
    GET_A_LIST_OF_GAMEMODES("/gamemodes", Duration.ofDays(1).getSeconds()),

    // Maps
    GET_A_LIST_OF_MAPS("/maps", Duration.ofDays(1).getSeconds()),

    // Players
    SEARCH_FOR_A_SPECIFIC_PLAYER("/players", Duration.ofMinutes(10).getSeconds()),
    GET_PLAYER_SUMMARY("/players/%s/summary", Duration.ofMinutes(10).getSeconds()),
    GET_PLAYER_STATS_SUMMARY("/players/%s/stats/summary", Duration.ofMinutes(10).getSeconds()),
    GET_PLAYER_CAREER_STATS("/players/%s/stats/career", Duration.ofMinutes(10).getSeconds()),
    GET_PLAYER_STATS_WITH_LABELS("/players/%s/stats", Duration.ofMinutes(10).getSeconds()),
    GET_ALL_PLAYER_DATA("/players/%s", Duration.ofMinutes(10).getSeconds());

    public static final String BASE_URL = "https://overfast-api.tekrop.fr";

    private final String path;
    private final long ttlSeconds;

    public String getPath(Object... args) {
        return String.format(path, args);
    }

    public String getUrl(Object... args) {
        return BASE_URL + getPath(args);
    }
}
