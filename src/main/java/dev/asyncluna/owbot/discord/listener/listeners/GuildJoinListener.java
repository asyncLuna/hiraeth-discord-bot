package dev.asyncluna.owbot.discord.listener.listeners;

import dev.asyncluna.owbot.core.model.GuildSettings;
import dev.asyncluna.owbot.core.repository.GuildSettingsRepository;
import dev.asyncluna.owbot.discord.listener.EventListener;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuildJoinListener implements EventListener<GuildCreateEvent> {
  private final GuildSettingsRepository guildSettingsRepository;

  @Override
  public Mono<Void> execute(GuildCreateEvent event) {
    return guildSettingsRepository
        .findById(event.getGuild().getId().asString())
        .switchIfEmpty(
            Mono.defer(
                () -> {
                  log.info(
                      "Creating default settings for guild: '{}'",
                      event.getGuild().getId().asString());
                  return guildSettingsRepository.save(
                      GuildSettings.builder().id(event.getGuild().getId().asString()).build());
                }))
        .then();
  }
}
