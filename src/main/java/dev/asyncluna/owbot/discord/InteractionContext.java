package dev.asyncluna.owbot.discord;

import dev.asyncluna.owbot.core.i18n.I18nManager;
import dev.asyncluna.owbot.core.i18n.SupportedLocale;
import dev.asyncluna.owbot.core.model.GuildSettings;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.entity.User;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Getter
public abstract class InteractionContext<E extends InteractionCreateEvent> {
  private final E event;
  private final GuildSettings guildSettings;
  private final I18nManager i18nManager;

  public GatewayDiscordClient getClient() {
    return event.getClient();
  }

  public User getAuthor() {
    return event.getInteraction().getUser();
  }

  public Snowflake getGuildId() {
    return event
        .getInteraction()
        .getGuildId()
        .orElseThrow(
            () -> new IllegalStateException("Interaction occurred outside of a guild context"));
  }

  public Locale getLocale() {
    return guildSettings != null
        ? SupportedLocale.forLanguageTag(guildSettings.getLocale()).getLocale()
        : SupportedLocale.ENGLISH.getLocale();
  }

  public String localize(String key, Object... args) {
    return i18nManager.localize(key, getLocale(), args);
  }
}
