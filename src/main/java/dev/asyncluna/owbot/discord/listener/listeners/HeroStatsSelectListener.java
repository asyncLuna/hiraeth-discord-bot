package dev.asyncluna.owbot.discord.listener.listeners;

import dev.asyncluna.owbot.core.i18n.I18nManager;
import dev.asyncluna.owbot.core.i18n.SupportedLocale;
import dev.asyncluna.owbot.core.integration.overfastapi.OverfastApiQueryParam;
import dev.asyncluna.owbot.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.owbot.core.integration.overfastapi.dto.HeroShort;
import dev.asyncluna.owbot.core.integration.overfastapi.dto.HeroStatsSummary;
import dev.asyncluna.owbot.core.model.GuildSettings;
import dev.asyncluna.owbot.core.repository.GuildSettingsRepository;
import dev.asyncluna.owbot.discord.listener.EventListener;
import dev.asyncluna.owbot.discord.util.EmbedUtils;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.spec.EmbedCreateSpec;
import io.sentry.util.StringUtils;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class HeroStatsSelectListener implements EventListener<SelectMenuInteractionEvent> {
  private final OverfastApiService overfastApiService;
  private final GuildSettingsRepository guildSettingsRepository;
  private final I18nManager i18nManager;

  @Override
  public Mono<Void> execute(SelectMenuInteractionEvent event) {
    String fullCustomId = event.getCustomId();

    String[] parts = fullCustomId.split(":", 2);
    String menuPrefix = parts[0];

    if (!"hs-1".equals(menuPrefix) && !"hs-2".equals(menuPrefix)) return Mono.empty();

    String ownerId = null;
    OverfastApiQueryParam.Platform parsedPlatform = null;
    String parsedGamemode = null;
    OverfastApiQueryParam.Region parsedRegion = null;
    OverfastApiQueryParam.Role parsedRole = null;
    String parsedMap = null;
    OverfastApiQueryParam.CompetitiveDivision parsedCompetitiveDivision = null;
    OverfastApiQueryParam.OrderBy parsedOrderBy = null;

    if (parts.length > 1) {
      String queryString = parts[1];
      for (String param : queryString.split("&")) {
        String[] keyValue = param.split("=", 2);
        if (keyValue.length == 2 && !"null".equals(keyValue[1])) {
          String rawValue = keyValue[1];
          String normalizedValue = rawValue.replace("-", "_").replace(" ", "_").toUpperCase();

          switch (keyValue[0]) {
            case "o" -> ownerId = rawValue;
            case "p" -> parsedPlatform = OverfastApiQueryParam.Platform.valueOf(normalizedValue);
            case "g" -> parsedGamemode = rawValue;
            case "reg" -> parsedRegion = OverfastApiQueryParam.Region.valueOf(normalizedValue);
            case "rol" -> parsedRole = OverfastApiQueryParam.Role.valueOf(normalizedValue);
            case "m" -> parsedMap = rawValue;
            case "c" ->
                parsedCompetitiveDivision =
                    OverfastApiQueryParam.CompetitiveDivision.valueOf(normalizedValue);
            case "ord" -> parsedOrderBy = OverfastApiQueryParam.OrderBy.valueOf(normalizedValue);
          }
        }
      }
    }

    String selectedHeroName = event.getValues().getFirst();
    String guildIdStr = event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");

    final String finalOwnerId = ownerId;
    final OverfastApiQueryParam.Platform finalPlatform = parsedPlatform;
    final String finalGamemode = parsedGamemode;
    final OverfastApiQueryParam.Region finalRegion = parsedRegion;
    final OverfastApiQueryParam.Role finalRole = parsedRole;
    final String finalMap = parsedMap;
    final OverfastApiQueryParam.CompetitiveDivision finalCompetitiveDivision =
        parsedCompetitiveDivision;
    final OverfastApiQueryParam.OrderBy finalOrderBy = parsedOrderBy;

    return guildSettingsRepository
        .findById(guildIdStr)
        .onErrorResume(
            exception -> {
              log.error("Database fallback triggered inside HeroStatsSelectListener", exception);
              return Mono.just(new GuildSettings());
            })
        .defaultIfEmpty(new GuildSettings())
        .flatMap(
            settings -> {
              Locale currentLocale =
                  SupportedLocale.forLanguageTag(settings.getLocale()).getLocale();

              String interactionUserId = event.getInteraction().getUser().getId().asString();
              if (finalOwnerId != null && !finalOwnerId.equals(interactionUserId)) {
                String localizedError = i18nManager.localize("error.menu_not_owned", currentLocale);
                return event
                    .reply()
                    .withEphemeral(true)
                    .withContent(localizedError)
                    .then(Mono.empty());
              }

              Mono<Map<String, HeroShort>> heroesMapMono =
                  overfastApiService
                      .getHeroes(null, null, null)
                      .collect(Collectors.toMap(hero -> hero.key().toLowerCase(), hero -> hero));

              Mono<HeroStatsSummary> statsMono =
                  overfastApiService
                      .getHeroStats(
                          finalPlatform,
                          finalGamemode,
                          finalRegion,
                          finalRole,
                          finalMap,
                          finalCompetitiveDivision,
                          finalOrderBy)
                      .filter(stats -> stats.hero().equalsIgnoreCase(selectedHeroName))
                      .next();

              return Mono.zip(statsMono, heroesMapMono)
                  .flatMap(
                      tuple -> {
                        HeroStatsSummary heroStats = tuple.getT1();
                        Map<String, HeroShort> heroesMap = tuple.getT2();

                        HeroShort heroDetails = heroesMap.get(heroStats.hero().toLowerCase());
                        String title = heroDetails != null ? heroDetails.name() : heroStats.hero();
                        String thumbnail = heroDetails != null ? heroDetails.portrait() : null;

                        EmbedCreateSpec.Builder updatedEmbedBuilder =
                            EmbedCreateSpec.builder().title(title).color(EmbedUtils.DEFAULT_COLOR);

                        if (heroDetails != null && heroDetails.role() != null)
                          updatedEmbedBuilder.addField(
                              i18nManager.localize("hero_stats.role", currentLocale),
                              StringUtils.capitalize(heroDetails.role()),
                              true);

                        if (heroDetails != null && heroDetails.subrole() != null)
                          updatedEmbedBuilder.addField(
                              i18nManager.localize("hero_stats.sub_role", currentLocale),
                              StringUtils.capitalize(heroDetails.subrole()),
                              true);

                        updatedEmbedBuilder
                            .addField(EmbedUtils.EMPTY_FIELD)
                            .addField(
                                i18nManager.localize("hero_stats.platform", currentLocale),
                                finalPlatform.getFriendlyName(),
                                true)
                            .addField(
                                i18nManager.localize("hero_stats.gamemode", currentLocale),
                                StringUtils.capitalize(finalGamemode),
                                true)
                            .addField(
                                i18nManager.localize("hero_stats.region", currentLocale),
                                finalRegion.getFriendlyName(),
                                true);

                        if (finalMap != null)
                          updatedEmbedBuilder.addField(
                              i18nManager.localize("hero_stats.map", currentLocale),
                              StringUtils.capitalize(finalMap),
                              true);

                        if (finalCompetitiveDivision != null)
                          updatedEmbedBuilder.addField(
                              i18nManager.localize(
                                  "hero_stats.competitive_division", currentLocale),
                              finalCompetitiveDivision.getFriendlyName(),
                              true);

                        if (finalOrderBy != null)
                          updatedEmbedBuilder.addField(
                              i18nManager.localize("hero_stats.order_by", currentLocale),
                              finalOrderBy.getFriendlyName(),
                              true);

                        updatedEmbedBuilder
                            .addField(EmbedUtils.EMPTY_FIELD)
                            .addField(
                                i18nManager.localize("hero_stats.pickrate", currentLocale),
                                heroStats.pickrate() + "%",
                                true)
                            .addField(
                                i18nManager.localize("hero_stats.winrate", currentLocale),
                                heroStats.winrate() + "%",
                                true);

                        if (thumbnail != null) updatedEmbedBuilder.thumbnail(thumbnail);

                        return event.edit().withEmbeds(updatedEmbedBuilder.build());
                      });
            })
        .then();
  }
}
