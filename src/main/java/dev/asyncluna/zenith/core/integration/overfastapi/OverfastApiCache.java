package dev.asyncluna.zenith.core.integration.overfastapi;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class OverfastApiCache {
    public static final String ALL_KEY = "all";
    private final Map<OverfastApiEndpoint, Cache<String, Object>> caches = new ConcurrentHashMap<>();

    public <T> void put(OverfastApiEndpoint endpoint, String key, T value, long ttlSeconds) {
        caches.computeIfAbsent(endpoint, __ -> Caffeine.newBuilder()
                        .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                        .build())
                .put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(OverfastApiEndpoint endpoint, String key, Class<T> classType) {
        Cache<String, Object> cache = caches.get(endpoint);
        if (cache == null) return null;
        Object value = cache.getIfPresent(key);
        return classType.isInstance(value) ? (T) value : null;
    }

    public void invalidate(OverfastApiEndpoint endpoint, String key) {
        Cache<String, Object> cache = caches.get(endpoint);
        if (cache != null) cache.invalidate(key);
    }
}
