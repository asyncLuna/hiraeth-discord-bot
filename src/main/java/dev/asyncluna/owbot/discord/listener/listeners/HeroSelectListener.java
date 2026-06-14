package dev.asyncluna.owbot.discord.listener.listeners;

import dev.asyncluna.owbot.core.i18n.I18nManager;
import dev.asyncluna.owbot.core.i18n.SupportedLocale;
import dev.asyncluna.owbot.core.integration.overfastapi.OverfastApiQueryParam;
import dev.asyncluna.owbot.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.owbot.core.model.GuildSettings;
import dev.asyncluna.owbot.core.repository.GuildSettingsRepository;
import dev.asyncluna.owbot.discord.listener.EventListener;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import io.sentry.util.StringUtils;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class HeroSelectListener implements EventListener<SelectMenuInteractionEvent> {
  private final OverfastApiService overfastApiService;
  private final GuildSettingsRepository guildSettingsRepository;
  private final I18nManager i18nManager;

  @Override
  public Mono<Void> execute(SelectMenuInteractionEvent event) {
    String fullCustomId = event.getCustomId();

    String[] parts = fullCustomId.split(":", 2);
    String menuPrefix = parts[0];

    if (!"hero-select-1".equals(menuPrefix) && !"hero-select-2".equals(menuPrefix))
      return Mono.empty();

    String ownerId = null;
    OverfastApiQueryParam.Role parsedRole = null;
    OverfastApiQueryParam.Locale parsedLocale = null;
    OverfastApiQueryParam.Gamemode parsedGamemode = null;

    if (parts.length > 1) {
      String queryString = parts[1];
      for (String param : queryString.split("&")) {
        String[] keyValue = param.split("=", 2);
        if (keyValue.length == 2 && !"null".equals(keyValue[1])) {
          String normalizedValue = keyValue[1].replace("-", "_").replace(" ", "_").toUpperCase();

          switch (keyValue[0]) {
            case "owner" -> ownerId = keyValue[1];
            case "role" -> parsedRole = OverfastApiQueryParam.Role.valueOf(normalizedValue);
            case "locale" -> parsedLocale = OverfastApiQueryParam.Locale.valueOf(normalizedValue);
            case "gamemode" ->
                parsedGamemode = OverfastApiQueryParam.Gamemode.valueOf(normalizedValue);
          }
        }
      }
    }

    String interactionUserId = event.getInteraction().getUser().getId().asString();
    if (ownerId != null && !ownerId.equals(interactionUserId)) {
      return event
          .reply()
          .withEphemeral(true)
          .withContent("❌ Only the user who ran the command can use this menu!");
    }

    String selectedHeroKey = event.getValues().getFirst();
    String guildIdStr = event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");

    final OverfastApiQueryParam.Role finalRole = parsedRole;
    final OverfastApiQueryParam.Locale finalLocale = parsedLocale;
    final OverfastApiQueryParam.Gamemode finalGamemode = parsedGamemode;

    return guildSettingsRepository
        .findById(guildIdStr)
        .onErrorResume(
            exception -> {
              log.error("Database fallback triggered inside HeroSelectListener", exception);
              return Mono.just(new GuildSettings());
            })
        .defaultIfEmpty(new GuildSettings())
        .flatMap(
            settings -> {
              Locale currentLocale =
                  SupportedLocale.forLanguageTag(settings.getLocale()).getLocale();

              return overfastApiService
                  .getHeroes(finalRole, finalLocale, finalGamemode)
                  .filter(hero -> hero.key().equalsIgnoreCase(selectedHeroKey))
                  .next()
                  .flatMap(
                      hero -> {
                        boolean availableInStadium =
                            hero.gamemodes().stream().anyMatch("stadium"::equalsIgnoreCase);

                        String stadiumStatus =
                            availableInStadium
                                ? "✅ " + i18nManager.localize("yes", currentLocale)
                                : "❌ " + i18nManager.localize("no", currentLocale);

                        EmbedCreateSpec updatedEmbed =
                            EmbedCreateSpec.builder()
                                .title(hero.name())
                                .thumbnail(hero.portrait())
                                .color(Color.of(255, 156, 0))
                                .addField(
                                    i18nManager.localize("heroes.role", currentLocale),
                                    StringUtils.capitalize(hero.role()),
                                    true)
                                .addField(
                                    i18nManager.localize("heroes.sub_role", currentLocale),
                                    StringUtils.capitalize(hero.subrole()),
                                    true)
                                .addField(
                                    i18nManager.localize(
                                        "heroes.available_in_stadium", currentLocale),
                                    stadiumStatus,
                                    true)
                                .build();

                        return event.edit().withEmbeds(updatedEmbed);
                      });
            })
        .then();
  }
}
