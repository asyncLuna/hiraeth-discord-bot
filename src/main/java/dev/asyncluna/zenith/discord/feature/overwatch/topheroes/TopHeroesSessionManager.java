package dev.asyncluna.zenith.discord.feature.overwatch.topheroes;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class TopHeroesSessionManager {
  private final Cache<String, TopHeroesSession> sessions =
      Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).maximumSize(10000).build();

  public void put(String sessionId, TopHeroesSession session) {
    sessions.put(sessionId, session);
  }

  public TopHeroesSession get(String sessionId) {
    return sessions.getIfPresent(sessionId);
  }
}
