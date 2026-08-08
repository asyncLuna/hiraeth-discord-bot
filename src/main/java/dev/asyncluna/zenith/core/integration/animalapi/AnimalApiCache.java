package dev.asyncluna.zenith.core.integration.animalapi;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class AnimalApiCache {
  private final ConcurrentHashMap<String, Object> cacheStore = new ConcurrentHashMap<>();

  @SuppressWarnings("unchecked")
  public <T> T get(AnimalApiEndpoint endpoint, String cacheKey, Class<T> type) {
    String fullKey = endpoint.name() + ":" + cacheKey;
    return (T) cacheStore.get(fullKey);
  }

  public <T> void put(AnimalApiEndpoint endpoint, String cacheKey, T data, long ttlSeconds) {
    String fullKey = endpoint.name() + ":" + cacheKey;
    cacheStore.put(fullKey, data);
  }
}
