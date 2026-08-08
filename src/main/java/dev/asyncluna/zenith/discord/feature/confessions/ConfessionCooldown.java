package dev.asyncluna.zenith.discord.feature.confessions;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ConfessionCooldown {
  private final Cache<String, Boolean> cooldowns =
      Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build();

  public boolean isActive(String guildId, String userId) {
    return cooldowns.getIfPresent(key(guildId, userId)) != null;
  }

  public boolean tryStart(String guildId, String userId) {
    return cooldowns.asMap().putIfAbsent(key(guildId, userId), Boolean.TRUE) == null;
  }

  public void clear(String guildId, String userId) {
    cooldowns.invalidate(key(guildId, userId));
  }

  private String key(String guildId, String userId) {
    return guildId + ":" + userId;
  }
}
