package dev.asyncluna.zenith.core.integration.overfastapi;

import dev.asyncluna.zenith.core.integration.overfastapi.dto.GamemodeDetails;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.Hero;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.HeroShort;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.HeroStatsSummary;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.Map;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.PlayerCareerStats;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.PlayerSummary;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.RoleDetail;
import dev.asyncluna.zenith.core.util.HttpUtils;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
@Slf4j
public class OverfastApiService {
    private final OverfastApiClient client;

    public Flux<HeroShort> getHeroes() {
        return getHeroes(null, OverfastApiQueryParam.Locale.EN_US, null);
    }

    public Flux<HeroShort> getHeroes(
            OverfastApiQueryParam.Role role, OverfastApiQueryParam.Locale locale, String gamemode) {
        log.info("Fetching heroes from Overfast API with role={}, locale={}, gamemode={}", role, locale, gamemode);

        String roleKey = role != null ? role.toString() : OverfastApiCache.ALL_KEY;
        String localeKey = locale != null ? locale.toString() : OverfastApiQueryParam.Locale.EN_US.toString();
        String gameKey = gamemode != null ? gamemode : OverfastApiCache.ALL_KEY;

        String cacheKey = String.format("heroes:role:%s:locale:%s:game:%s", roleKey, localeKey, gameKey);

        OverfastApiQueryParams queryParams = OverfastApiQueryParams.builder()
                .role(role)
                .locale(locale)
                .gamemode(gamemode)
                .build();

        Function<UriBuilder, URI> uriFunction = builder -> {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(OverfastApiEndpoint.BASE_URL)
                    .path(OverfastApiEndpoint.GET_A_LIST_OF_HEROES.getPath());
            if (role != null) uriBuilder.queryParam("role", role.toString().toLowerCase());
            if (locale != null) uriBuilder.queryParam("locale", locale.toString());
            if (gamemode != null) uriBuilder.queryParam("gamemode", gamemode);
            return uriBuilder.build().toUri();
        };

        return client.get(OverfastApiEndpoint.GET_A_LIST_OF_HEROES, cacheKey, uriFunction, HeroShort[].class)
                .contextWrite(context -> context.put(HttpUtils.PARAM_CONTEXT_KEY, queryParams.asMap()))
                .flatMapMany(Flux::fromArray);
    }

    public Flux<GamemodeDetails> getGamemodes() {
        Function<UriBuilder, URI> uriFunction =
                builder -> UriComponentsBuilder.fromUriString(OverfastApiEndpoint.BASE_URL)
                        .path(OverfastApiEndpoint.GET_A_LIST_OF_GAMEMODES.getPath())
                        .build()
                        .toUri();
        return client.get(
                        OverfastApiEndpoint.GET_A_LIST_OF_GAMEMODES,
                        OverfastApiCache.ALL_KEY,
                        uriFunction,
                        GamemodeDetails[].class)
                .flatMapMany(Flux::fromArray);
    }

    public Flux<HeroStatsSummary> getHeroStats(
            OverfastApiQueryParam.Platform platform, String gamemode, OverfastApiQueryParam.Region region) {
        return getHeroStats(platform, gamemode, region, null, null, null, null);
    }

    public Flux<HeroStatsSummary> getHeroStats(
            OverfastApiQueryParam.Platform platform,
            String gamemode,
            OverfastApiQueryParam.Region region,
            OverfastApiQueryParam.Role role,
            String map,
            OverfastApiQueryParam.CompetitiveDivision competitiveDivision,
            OverfastApiQueryParam.OrderBy orderBy) {
        log.info(
                "Fetching hero stats from Overfast API with platform={}, gamemode={}, region={}, role={}, map={}, competitiveDivision={}, orderBy={}",
                platform,
                gamemode,
                region,
                role,
                map,
                competitiveDivision,
                orderBy);

        String platformKey = platform != null ? platform.toString() : OverfastApiCache.ALL_KEY;
        String gameKey = gamemode != null ? gamemode : OverfastApiCache.ALL_KEY;
        String regionKey = region != null ? region.toString() : OverfastApiCache.ALL_KEY;
        String roleKey = role != null ? role.toString() : OverfastApiCache.ALL_KEY;
        String mapKey = map != null ? map : OverfastApiCache.ALL_KEY;
        String competitiveDivisionKey =
                competitiveDivision != null ? competitiveDivision.toString() : OverfastApiCache.ALL_KEY;
        String orderByKey = orderBy != null ? orderBy.toString() : OverfastApiCache.ALL_KEY;

        String cacheKey = String.format(
                "heroStats:platform:%s:gamemode:%s:region:%s:role:%s:map:%s:competitiveDivision:%s:orderBy:%s",
                platformKey, gameKey, regionKey, roleKey, mapKey, competitiveDivisionKey, orderByKey);

        OverfastApiQueryParams queryParams = OverfastApiQueryParams.builder()
                .platform(platform)
                .gamemode(gamemode)
                .region(region)
                .role(role)
                .map(map)
                .competitiveDivision(competitiveDivision)
                .orderBy(orderBy)
                .build();

        Function<UriBuilder, URI> uriFunction = builder -> {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(OverfastApiEndpoint.BASE_URL)
                    .path(OverfastApiEndpoint.GET_HERO_STATS.getPath());
            if (platform != null)
                uriBuilder.queryParam("platform", platform.toString().toLowerCase());
            if (gamemode != null) uriBuilder.queryParam("gamemode", gamemode.toLowerCase());
            if (region != null)
                uriBuilder.queryParam("region", region.toString().toLowerCase());
            if (role != null) uriBuilder.queryParam("role", role.toString().toLowerCase());
            if (map != null) uriBuilder.queryParam("map", map);
            if (competitiveDivision != null)
                uriBuilder.queryParam(
                        "competitive_division", competitiveDivision.toString().toLowerCase());
            if (orderBy != null)
                uriBuilder.queryParam("order_by", orderBy.toString().toLowerCase());
            return uriBuilder.build().toUri();
        };

        return client.get(OverfastApiEndpoint.GET_HERO_STATS, cacheKey, uriFunction, HeroStatsSummary[].class)
                .contextWrite(context -> context.put(HttpUtils.PARAM_CONTEXT_KEY, queryParams.asMap()))
                .flatMapMany(Flux::fromArray);
    }

    public Flux<Map> getMaps() {
        return getMaps(null);
    }

    public Flux<Map> getMaps(String gamemode) {
        log.info("Fetching maps from Overfast API with gamemode={}", gamemode);

        String gamemodeKey = gamemode != null ? gamemode : OverfastApiCache.ALL_KEY;

        String cacheKey = String.format("maps:gamemode:%s", gamemodeKey);

        OverfastApiQueryParams queryParams =
                OverfastApiQueryParams.builder().gamemode(gamemode).build();

        Function<UriBuilder, URI> uriFunction = builder -> {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(OverfastApiEndpoint.BASE_URL)
                    .path(OverfastApiEndpoint.GET_A_LIST_OF_MAPS.getPath());
            if (gamemode != null) uriBuilder.queryParam("gamemode", gamemode);
            return uriBuilder.build().toUri();
        };

        return client.get(OverfastApiEndpoint.GET_A_LIST_OF_MAPS, cacheKey, uriFunction, Map[].class)
                .contextWrite(context -> context.put(HttpUtils.PARAM_CONTEXT_KEY, queryParams.asMap()))
                .flatMapMany(Flux::fromArray);
    }

    public Mono<Hero> getHeroData(String heroKey) {
        return getHeroData(heroKey, OverfastApiQueryParam.Locale.EN_US);
    }

    public Mono<Hero> getHeroData(String heroKey, OverfastApiQueryParam.Locale locale) {
        log.info("Fetching hero data from Overfast API for heroKey={} with locale={}", heroKey, locale);

        String localeKey = locale != null ? locale.toString() : OverfastApiQueryParam.Locale.EN_US.toString();

        String cacheKey = String.format("heroData:heroKey:%s:locale:%s", heroKey, localeKey);

        OverfastApiQueryParams queryParams =
                OverfastApiQueryParams.builder().heroKey(heroKey).locale(locale).build();

        Function<UriBuilder, URI> uriFunction = builder -> {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(OverfastApiEndpoint.BASE_URL)
                    .path(OverfastApiEndpoint.GET_HERO_DATA.getPath(heroKey));
            if (locale != null) uriBuilder.queryParam("locale", locale.toString());
            return uriBuilder.build().toUri();
        };

        return client.get(OverfastApiEndpoint.GET_HERO_DATA, cacheKey, uriFunction, Hero.class)
                .contextWrite(context -> context.put(HttpUtils.PARAM_CONTEXT_KEY, queryParams.asMap()));
    }

    public Flux<RoleDetail> getRoles() {
        return getRoles(OverfastApiQueryParam.Locale.EN_US);
    }

    public Flux<RoleDetail> getRoles(OverfastApiQueryParam.Locale locale) {
        log.info("Fetching roles from Overfast API with locale={}", locale);

        String localeKey = locale != null ? locale.toString() : OverfastApiQueryParam.Locale.EN_US.toString();

        String cacheKey = String.format("roles:locale:%s", localeKey);

        OverfastApiQueryParams queryParams =
                OverfastApiQueryParams.builder().locale(locale).build();

        Function<UriBuilder, URI> uriFunction = builder -> {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(OverfastApiEndpoint.BASE_URL)
                    .path(OverfastApiEndpoint.GET_A_LIST_OF_ROLES.getPath());
            if (locale != null) uriBuilder.queryParam("locale", locale.toString());
            return uriBuilder.build().toUri();
        };

        return client.get(OverfastApiEndpoint.GET_A_LIST_OF_ROLES, cacheKey, uriFunction, RoleDetail[].class)
                .contextWrite(context -> context.put(HttpUtils.PARAM_CONTEXT_KEY, queryParams.asMap()))
                .flatMapMany(Flux::fromArray);
    }

    public Mono<PlayerSummary> getPlayerSummary(String playerId) {
        log.info("Fetching player summary from Overfast API for playerId={}", playerId);

        String formattedPlayerId = normalizePlayerId(playerId);
        String cacheKey = String.format("playerSummary:id:%s", formattedPlayerId);

        Function<UriBuilder, URI> uriFunction =
                builder -> UriComponentsBuilder.fromUriString(OverfastApiEndpoint.BASE_URL)
                        .path(OverfastApiEndpoint.GET_PLAYER_SUMMARY.getPath(formattedPlayerId))
                        .encode()
                        .build()
                        .toUri();

        return client.get(OverfastApiEndpoint.GET_PLAYER_SUMMARY, cacheKey, uriFunction, PlayerSummary.class);
    }

    public Mono<PlayerCareerStats> getPlayerCareerStats(String playerId, String gamemode) {
        log.info("Fetching career stats from Overfast API for playerId={}, gamemode={}", playerId, gamemode);
        String formattedPlayerId = normalizePlayerId(playerId);
        String cacheKey = String.format("playerCareerStats:id:%s:gamemode:%s", formattedPlayerId, gamemode);

        Function<UriBuilder, URI> uriFunction =
                builder -> UriComponentsBuilder.fromUriString(OverfastApiEndpoint.BASE_URL)
                        .path(OverfastApiEndpoint.GET_PLAYER_CAREER_STATS.getPath(formattedPlayerId))
                        .queryParam("gamemode", gamemode)
                        .encode()
                        .build()
                        .toUri();

        return client.get(OverfastApiEndpoint.GET_PLAYER_CAREER_STATS, cacheKey, uriFunction, JsonNode.class)
                .map(PlayerCareerStats::new);
    }

    private String normalizePlayerId(String playerId) {
        return UriUtils.decode(playerId, StandardCharsets.UTF_8).replace("#", "-");
    }
}
