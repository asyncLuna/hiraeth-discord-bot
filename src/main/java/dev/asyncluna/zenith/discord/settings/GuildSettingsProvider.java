package dev.asyncluna.zenith.discord.settings;

import dev.asyncluna.zenith.core.model.GuildSettings;
import dev.asyncluna.zenith.core.repository.GuildSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuildSettingsProvider {
  private final GuildSettingsRepository repository;

  public Mono<GuildSettings> get(String guildId) {
    return repository
        .findById(guildId)
        .onErrorResume(
            exception -> {
              log.error("Failed to load guild settings | guildId={}", guildId, exception);
              return Mono.just(new GuildSettings());
            })
        .defaultIfEmpty(new GuildSettings());
  }
}
