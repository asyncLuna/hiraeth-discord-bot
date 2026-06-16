package dev.asyncluna.owbot.core.integration.overfastapi;

import dev.asyncluna.owbot.core.integration.overfastapi.dto.GamemodeDetails;
import dev.asyncluna.owbot.core.integration.overfastapi.dto.Hero;
import dev.asyncluna.owbot.core.integration.overfastapi.dto.HeroShort;
import dev.asyncluna.owbot.core.integration.overfastapi.dto.HeroStatsSummary;
import dev.asyncluna.owbot.core.integration.overfastapi.dto.Map;
import dev.asyncluna.owbot.core.util.HttpUtils;
import java.io.IOException;
import java.net.URI;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class OverfastApiService {
  private final OverfastApiCache cache;

  public Flux<HeroShort> getHeroes() {
    return getHeroes(null, OverfastApiQueryParam.Locale.EN_US, null);
  }

  public Flux<HeroShort> getHeroes(
      OverfastApiQueryParam.Role role, OverfastApiQueryParam.Locale locale, String gamemode) {
    log.debug(
        "Fetching heroes from Overfast API with role={}, locale={}, gamemode={}",
        role,
        locale,
        gamemode);

    String roleKey = role != null ? role.toString() : OverfastApiCache.ALL_KEY;
    String localeKey =
        locale != null ? locale.toString() : OverfastApiQueryParam.Locale.EN_US.toString();
    String gameKey = gamemode != null ? gamemode : OverfastApiCache.ALL_KEY;

    String cacheKey =
        String.format("heroes:role:%s:locale:%s:game:%s", roleKey, localeKey, gameKey);

    OverfastApiQueryParams queryParams =
        OverfastApiQueryParams.builder().role(role).locale(locale).gamemode(gamemode).build();

    Function<UriBuilder, URI> uriFunction =
        builder -> {
          builder.path(OverfastApiEndpoint.GET_A_LIST_OF_HEROES.getPath());
          if (role != null) builder.queryParam("role", role.toString().toLowerCase());
          if (locale != null) builder.queryParam("locale", locale.toString());
          if (gamemode != null) builder.queryParam("gamemode", gamemode);
          return builder.build();
        };

    return getWithCache(
            OverfastApiEndpoint.GET_A_LIST_OF_HEROES, cacheKey, uriFunction, HeroShort[].class)
        .contextWrite(context -> context.put(HttpUtils.PARAM_CONTEXT_KEY, queryParams))
        .flatMapMany(Flux::fromArray);
  }

  public Flux<GamemodeDetails> getGamemodes() {
    Function<UriBuilder, URI> uriFunction =
        builder -> builder.path(OverfastApiEndpoint.GET_A_LIST_OF_GAMEMODES.getPath()).build();
    return getWithCache(
            OverfastApiEndpoint.GET_A_LIST_OF_GAMEMODES,
            OverfastApiCache.ALL_KEY,
            uriFunction,
            GamemodeDetails[].class)
        .flatMapMany(Flux::fromArray);
  }

  public Flux<HeroStatsSummary> getHeroStats(
      OverfastApiQueryParam.Platform platform,
      String gamemode,
      OverfastApiQueryParam.Region region) {
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
    log.debug(
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

    String cacheKey =
        String.format(
            "heroStats:platform:%s:gamemode:%s:region:%s:role:%s:map:%s:competitiveDivision:%s:orderBy:%s",
            platformKey, gameKey, regionKey, roleKey, mapKey, competitiveDivisionKey, orderByKey);

    OverfastApiQueryParams queryParams =
        OverfastApiQueryParams.builder()
            .platform(platform)
            .gamemode(gamemode)
            .region(region)
            .role(role)
            .map(map)
            .competitiveDivision(competitiveDivision)
            .orderBy(orderBy)
            .build();

    Function<UriBuilder, URI> uriFunction =
        builder -> {
          builder.path(OverfastApiEndpoint.GET_HERO_STATS.getPath());
          if (platform != null) builder.queryParam("platform", platform.toString().toLowerCase());
          if (gamemode != null) builder.queryParam("gamemode", gamemode.toLowerCase());
          if (region != null) builder.queryParam("region", region.toString().toLowerCase());
          if (role != null) builder.queryParam("role", role.toString().toLowerCase());
          if (map != null) builder.queryParam("map", map);
          if (competitiveDivision != null)
            builder.queryParam(
                "competitive_division", competitiveDivision.toString().toLowerCase());
          if (orderBy != null) builder.queryParam("order_by", orderBy.toString().toLowerCase());
          return builder.build();
        };

    return getWithCache(
            OverfastApiEndpoint.GET_HERO_STATS, cacheKey, uriFunction, HeroStatsSummary[].class)
        .contextWrite(context -> context.put(HttpUtils.PARAM_CONTEXT_KEY, queryParams))
        .flatMapMany(Flux::fromArray);
  }

  public Flux<Map> getMaps() {
    log.debug("Fetching maps from Overfast API");

    Function<UriBuilder, URI> uriFunction =
        builder -> builder.path(OverfastApiEndpoint.GET_A_LIST_OF_MAPS.getPath()).build();
    return getWithCache(
            OverfastApiEndpoint.GET_A_LIST_OF_MAPS,
            OverfastApiCache.ALL_KEY,
            uriFunction,
            Map[].class)
        .flatMapMany(Flux::fromArray);
  }

  public Mono<Hero> getHeroData(String heroKey) {
    return getHeroData(heroKey, OverfastApiQueryParam.Locale.EN_US);
  }

  public Mono<Hero> getHeroData(String heroKey, OverfastApiQueryParam.Locale locale) {
    log.debug(
        "Fetching hero data from Overfast API for heroKey={} with locale={}", heroKey, locale);

    String localeKey =
        locale != null ? locale.toString() : OverfastApiQueryParam.Locale.EN_US.toString();

    String cacheKey = String.format("heroData:heroKey:%s:locale:%s", heroKey, localeKey);

    OverfastApiQueryParams queryParams =
        OverfastApiQueryParams.builder().heroKey(heroKey).locale(locale).build();

    Function<UriBuilder, URI> uriFunction =
        builder -> {
          builder.path(OverfastApiEndpoint.GET_HERO_DATA.getPath(heroKey));
          if (locale != null) builder.queryParam("locale", locale.toString());
          return builder.build();
        };

    return getWithCache(OverfastApiEndpoint.GET_HERO_DATA, cacheKey, uriFunction, Hero.class)
        .contextWrite(context -> context.put(HttpUtils.PARAM_CONTEXT_KEY, queryParams));
  }

  private <T> Mono<T> getWithCache(
      OverfastApiEndpoint endpoint,
      String cacheKey,
      Function<UriBuilder, URI> uriFunction,
      Class<T> type) {
    T cached = cache.get(endpoint, cacheKey, type);
    if (cached != null) {
      log.debug("Cache hit for endpoint={} with cacheKey={}", endpoint, cacheKey);
      return Mono.just(cached);
    }

    return HttpUtils.WEB_CLIENT
        .get()
        .uri(
            uriBuilder -> {
              URI finalUri = uriFunction.apply(uriBuilder);
              log.debug("Outbound WebClient executing URI request layout: {}", finalUri);
              return finalUri;
            })
        .retrieve()
        .bodyToMono(String.class)
        .doOnNext(json -> log.debug("Received JSON response for endpoint [{}]: {}", endpoint, json))
        .map(json -> HttpUtils.OBJECT_MAPPER.readValue(json, type))
        .onErrorMap(
            IOException.class,
            exception -> new RuntimeException("Failed to parse JSON response", exception))
        .doOnNext(response -> cache.put(endpoint, cacheKey, response, endpoint.getTtlSeconds()))
        .then(
            Mono.defer(
                () -> {
                  T response = cache.get(endpoint, cacheKey, type);
                  if (response != null) {
                    log.debug("Cache updated for endpoint={} with cacheKey={}", endpoint, cacheKey);
                    return Mono.just(response);
                  } else {
                    log.warn("Failed to find cached item after population for key={}", cacheKey);
                    return Mono.empty();
                  }
                }));
  }
}
