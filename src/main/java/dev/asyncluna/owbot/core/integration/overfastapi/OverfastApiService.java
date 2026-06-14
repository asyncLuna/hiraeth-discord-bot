package dev.asyncluna.owbot.core.integration.overfastapi;

import dev.asyncluna.owbot.core.integration.overfastapi.dto.HeroShort;
import dev.asyncluna.owbot.core.util.HttpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class OverfastApiService {
  private final OverfastApiCache cache;

  public Flux<HeroShort> getHeroes() {
    return getHeroes(null, null, null);
  }

  public Flux<HeroShort> getHeroes(
      OverfastApiQueryParam.Role role,
      OverfastApiQueryParam.Locale locale,
      OverfastApiQueryParam.Gamemode gamemode) {
    log.debug("Fetching heroes with role={}, locale={}, gamemode={}", role, locale, gamemode);

    String roleKey = role != null ? role.toString() : OverfastApiCache.ALL_KEY;
    String localeKey =
        locale != null ? locale.toString() : OverfastApiQueryParam.Locale.EN_US.toString();
    String gameKey = gamemode != null ? gamemode.toString() : OverfastApiCache.ALL_KEY;

    String cacheKey =
        String.format("heroes:role:%s:locale:%s:game:%s", roleKey, localeKey, gameKey);

    OverfastApiQueryParams queryParams =
        OverfastApiQueryParams.builder().role(role).locale(locale).gamemode(gamemode).build();

    return getWithCache(OverfastApiEndpoint.GET_A_LIST_OF_HEROES, cacheKey, HeroShort[].class)
        .contextWrite(context -> context.put(HttpUtils.PARAM_CONTEXT_KEY, queryParams))
        .flatMapMany(Flux::fromArray);
  }

  private <T> Mono<T> getWithCache(OverfastApiEndpoint endpoint, String cacheKey, Class<T> type) {
    T cached = cache.get(endpoint, cacheKey, type);
    if (cached != null) {
      log.debug("Cache hit for endpoint={} with cacheKey={}", endpoint, cacheKey);
      return Mono.just(cached);
    }

    return HttpUtils.WEB_CLIENT
        .get()
        .uri(endpoint.getUrl(cacheKey))
        .retrieve()
        .bodyToMono(type)
        .doOnNext(response -> cache.put(endpoint, cacheKey, response, endpoint.getTtlSeconds()))
        .then(
            Mono.defer(
                () -> {
                  T response = cache.get(endpoint, cacheKey, type);
                  if (response != null) {
                    log.debug("Cache updated for endpoint={} with cacheKey={}", endpoint, cacheKey);
                    return Mono.just(response);
                  } else {
                    log.warn(
                        "Failed to update cache for endpoint={} with cacheKey={}",
                        endpoint,
                        cacheKey);
                    return Mono.empty();
                  }
                }));
  }
}
