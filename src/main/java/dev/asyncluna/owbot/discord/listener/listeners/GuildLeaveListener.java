package dev.asyncluna.owbot.discord.listener.listeners;

import dev.asyncluna.owbot.core.repository.GuildSettingsRepository;
import dev.asyncluna.owbot.discord.listener.EventListener;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuildLeaveListener implements EventListener<GuildDeleteEvent> {
  private final GuildSettingsRepository guildSettingsRepository;

  @Override
  public Mono<Void> execute(GuildDeleteEvent event) {
    return guildSettingsRepository
        .deleteById(event.getGuildId().asString())
        .doOnSuccess(
            unused -> log.info("Deleted settings for guild: '{}'", event.getGuildId().asString()));
  }
}
