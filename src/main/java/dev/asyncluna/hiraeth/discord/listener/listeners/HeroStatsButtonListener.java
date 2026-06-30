package dev.asyncluna.hiraeth.discord.listener.listeners;

import dev.asyncluna.hiraeth.core.i18n.I18nManager;
import dev.asyncluna.hiraeth.core.i18n.SupportedLocale;
import dev.asyncluna.hiraeth.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.hiraeth.core.integration.overfastapi.dto.Hero;
import dev.asyncluna.hiraeth.core.integration.overfastapi.dto.HeroShort;
import dev.asyncluna.hiraeth.core.integration.overfastapi.dto.HeroStatsSummary;
import dev.asyncluna.hiraeth.core.integration.overfastapi.dto.HitPoints;
import dev.asyncluna.hiraeth.core.model.GuildSettings;
import dev.asyncluna.hiraeth.core.repository.GuildSettingsRepository;
import dev.asyncluna.hiraeth.discord.command.commands.HeroStatsCommand;
import dev.asyncluna.hiraeth.discord.command.commands.HeroStatsCommand.HeroStatsSession;
import dev.asyncluna.hiraeth.discord.listener.EventListener;
import dev.asyncluna.hiraeth.discord.util.DiscordConstants;
import dev.asyncluna.hiraeth.discord.util.EmbedUtils;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.spec.EmbedCreateSpec;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class HeroStatsButtonListener implements EventListener<ButtonInteractionEvent> {
  private final OverfastApiService overfastApiService;
  private final GuildSettingsRepository guildSettingsRepository;
  private final I18nManager i18nManager;
  private final HeroStatsCommand heroStatsCommand;

  @Override
  public Mono<Void> execute(ButtonInteractionEvent event) {
    String fullCustomId = event.getCustomId();

    String[] parts = fullCustomId.split(":", 2);
    String btnPrefix = parts[0];

    if (!"hs-btn".equals(btnPrefix) && !"hs-back".equals(btnPrefix)) return Mono.empty();
    if (parts.length < 2) return Mono.empty();

    String sessionId = parts[1];
    HeroStatsSession session = heroStatsCommand.getSessionCache().getIfPresent(sessionId);
    String guildIdStr = event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");

    return guildSettingsRepository
        .findById(guildIdStr)
        .onErrorResume(
            exception -> {
              log.error("Database fallback triggered inside HeroStatsButtonListener", exception);
              return Mono.just(new GuildSettings());
            })
        .defaultIfEmpty(new GuildSettings())
        .flatMap(
            settings -> {
              Locale currentLocale =
                  SupportedLocale.forLanguageTag(settings.getLocale()).getLocale();

              if (session == null) {
                return event
                    .reply()
                    .withEphemeral(true)
                    .withContent(
                        i18nManager.localize(
                            "error.menu_interaction_expired", currentLocale, "hero_stats"));
              }

              String interactionUserId = event.getInteraction().getUser().getId().asString();
              if (!session.userId().equals(interactionUserId)) {
                String localizedError = i18nManager.localize("error.menu_not_owned", currentLocale);
                return event.reply().withEphemeral(true).withContent(localizedError);
              }

              if ("hs-back".equals(btnPrefix)) {
                return handleGoBack(event, session, sessionId, currentLocale);
              }

              String heroKey = session.getSelectedHeroKey();
              if (heroKey == null) return Mono.empty();

              return overfastApiService
                  .getHeroData(heroKey, null)
                  .flatMap(
                      heroData -> {
                        EmbedCreateSpec detailedEmbed =
                            createHeroDataEmbed(heroData, currentLocale);

                        String goBackLabel = i18nManager.localize("hero.go_back", currentLocale);
                        Button backButton = Button.danger("hs-back:" + sessionId, goBackLabel);

                        return event
                            .edit()
                            .withEmbeds(detailedEmbed)
                            .withComponents(ActionRow.of(backButton));
                      });
            })
        .then();
  }

  private Mono<Void> handleGoBack(
      ButtonInteractionEvent event,
      HeroStatsSession session,
      String sessionId,
      Locale currentLocale) {
    Mono<Map<String, HeroShort>> heroesMapMono =
        overfastApiService
            .getHeroes()
            .collect(Collectors.toMap(hero -> hero.key().toLowerCase(), hero -> hero));

    Mono<Map<String, String>> mapsNameMapMono =
        overfastApiService
            .getMaps()
            .collect(
                Collectors.toMap(
                    map -> map.key().toLowerCase(),
                    dev.asyncluna.hiraeth.core.integration.overfastapi.dto.Map::name));

    Mono<List<HeroStatsSummary>> statsMono =
        overfastApiService
            .getHeroStats(
                session.platform(),
                session.gamemode(),
                session.region(),
                session.role(),
                session.map(),
                session.competitiveDivision(),
                session.orderBy())
            .collectList();

    return Mono.zip(statsMono, heroesMapMono, mapsNameMapMono)
        .flatMap(
            tuple -> {
              List<HeroStatsSummary> heroStatsList = tuple.getT1();
              Map<String, HeroShort> heroesMap = tuple.getT2();
              Map<String, String> mapsNameMap = tuple.getT3();

              String currentHeroKey = session.getSelectedHeroKey();
              HeroStatsSummary currentHeroStats =
                  heroStatsList.stream()
                      .filter(stats -> stats.hero().equalsIgnoreCase(currentHeroKey))
                      .findFirst()
                      .orElse(heroStatsList.getFirst());

              HeroShort currentHeroDetails = heroesMap.get(currentHeroStats.hero().toLowerCase());
              String friendlyMapName =
                  session.map() != null
                      ? mapsNameMap.getOrDefault(session.map().toLowerCase(), session.map())
                      : null;

              EmbedCreateSpec.Builder backEmbedBuilder =
                  EmbedCreateSpec.builder()
                      .title(
                          currentHeroDetails != null
                              ? currentHeroDetails.name()
                              : WordUtils.capitalizeFully(currentHeroStats.hero()))
                      .color(EmbedUtils.DEFAULT_COLOR);

              if (session.role() != null)
                backEmbedBuilder.addField(
                    i18nManager.localize("hero.role", currentLocale),
                    session.role().getFriendlyName(),
                    true);

              backEmbedBuilder
                  .addField(
                      i18nManager.localize("hero.platform", currentLocale),
                      session.platform().getFriendlyName(),
                      true)
                  .addField(
                      i18nManager.localize("hero.gamemode", currentLocale),
                      WordUtils.capitalizeFully(session.gamemode()),
                      true)
                  .addField(
                      i18nManager.localize("hero.region", currentLocale),
                      session.region().getFriendlyName(),
                      true);

              if (friendlyMapName != null)
                backEmbedBuilder.addField(
                    i18nManager.localize("hero.map", currentLocale), friendlyMapName, true);
              if (session.competitiveDivision() != null)
                backEmbedBuilder.addField(
                    i18nManager.localize("hero.competitive_division", currentLocale),
                    session.competitiveDivision().getFriendlyName(),
                    true);
              if (session.orderBy() != null)
                backEmbedBuilder.addField(
                    i18nManager.localize("hero.order_by", currentLocale),
                    session.orderBy().getFriendlyName(),
                    true);

              backEmbedBuilder
                  .addField(
                      i18nManager.localize("hero.pickrate", currentLocale),
                      (currentHeroStats.pickrate() != null
                          ? currentHeroStats.pickrate() + "%"
                          : "N/A"),
                      true)
                  .addField(
                      i18nManager.localize("hero.winrate", currentLocale),
                      (currentHeroStats.winrate() != null
                          ? currentHeroStats.winrate() + "%"
                          : "N/A"),
                      true);

              if (currentHeroDetails != null && currentHeroDetails.portrait() != null)
                backEmbedBuilder.thumbnail(currentHeroDetails.portrait());

              String viewDetailsLabel = i18nManager.localize("hero.view_details", currentLocale);
              Button detailsButton = Button.primary("hs-btn:" + sessionId, viewDetailsLabel);

              String selectAHeroTemplate =
                  i18nManager.localize("hero.select_a_hero", currentLocale);

              if (heroStatsList.size() <= DiscordConstants.MAX_COMPONENT_ENTRIES) {
                SelectMenu selectMenu =
                    SelectMenu.of(
                            "hs-1:" + sessionId,
                            heroStatsList.stream()
                                .map(
                                    heroStatsSummary -> {
                                      HeroShort details =
                                          heroesMap.get(heroStatsSummary.hero().toLowerCase());
                                      String label =
                                          details != null
                                              ? details.name()
                                              : heroStatsSummary.hero();
                                      return SelectMenu.Option.of(label, heroStatsSummary.hero());
                                    })
                                .toList())
                        .withPlaceholder(
                            String.format(
                                selectAHeroTemplate,
                                heroesMap.containsKey(heroStatsList.getFirst().hero().toLowerCase())
                                    ? heroesMap
                                        .get(heroStatsList.getFirst().hero().toLowerCase())
                                        .name()
                                    : heroStatsList.getFirst().hero(),
                                heroesMap.containsKey(heroStatsList.getLast().hero().toLowerCase())
                                    ? heroesMap
                                        .get(heroStatsList.getLast().hero().toLowerCase())
                                        .name()
                                    : heroStatsList.getLast().hero()));

                return event
                    .edit()
                    .withEmbeds(backEmbedBuilder.build())
                    .withComponents(ActionRow.of(detailsButton), ActionRow.of(selectMenu));
              }

              List<HeroStatsSummary> firstHalfStats =
                  heroStatsList.stream().limit(DiscordConstants.MAX_COMPONENT_ENTRIES).toList();
              List<HeroStatsSummary> secondHalfStats =
                  heroStatsList.stream()
                      .skip(DiscordConstants.MAX_COMPONENT_ENTRIES)
                      .limit(DiscordConstants.MAX_COMPONENT_ENTRIES)
                      .toList();

              SelectMenu firstSelectMenu =
                  SelectMenu.of(
                          "hs-1:" + sessionId,
                          firstHalfStats.stream()
                              .map(
                                  heroStatsSummary -> {
                                    HeroShort details =
                                        heroesMap.get(heroStatsSummary.hero().toLowerCase());
                                    String label =
                                        details != null ? details.name() : heroStatsSummary.hero();
                                    return SelectMenu.Option.of(label, heroStatsSummary.hero());
                                  })
                              .toList())
                      .withPlaceholder(
                          String.format(
                              selectAHeroTemplate,
                              heroesMap.containsKey(firstHalfStats.getFirst().hero().toLowerCase())
                                  ? heroesMap
                                      .get(firstHalfStats.getFirst().hero().toLowerCase())
                                      .name()
                                  : firstHalfStats.getFirst().hero(),
                              heroesMap.containsKey(firstHalfStats.getLast().hero().toLowerCase())
                                  ? heroesMap
                                      .get(firstHalfStats.getLast().hero().toLowerCase())
                                      .name()
                                  : firstHalfStats.getLast().hero()));

              SelectMenu secondSelectMenu =
                  SelectMenu.of(
                          "hs-2:" + sessionId,
                          secondHalfStats.stream()
                              .map(
                                  heroStatsSummary -> {
                                    HeroShort details =
                                        heroesMap.get(heroStatsSummary.hero().toLowerCase());
                                    String label =
                                        details != null ? details.name() : heroStatsSummary.hero();
                                    return SelectMenu.Option.of(label, heroStatsSummary.hero());
                                  })
                              .toList())
                      .withPlaceholder(
                          String.format(
                              selectAHeroTemplate,
                              heroesMap.containsKey(secondHalfStats.getFirst().hero().toLowerCase())
                                  ? heroesMap
                                      .get(secondHalfStats.getFirst().hero().toLowerCase())
                                      .name()
                                  : secondHalfStats.getFirst().hero(),
                              heroesMap.containsKey(secondHalfStats.getLast().hero().toLowerCase())
                                  ? heroesMap
                                      .get(secondHalfStats.getLast().hero().toLowerCase())
                                      .name()
                                  : secondHalfStats.getLast().hero()));

              return event
                  .edit()
                  .withEmbeds(backEmbedBuilder.build())
                  .withComponents(
                      ActionRow.of(detailsButton),
                      ActionRow.of(firstSelectMenu),
                      ActionRow.of(secondSelectMenu));
            });
  }

  private EmbedCreateSpec createHeroDataEmbed(Hero heroData, Locale currentLocale) {
    String title = heroData != null ? heroData.name() : "";
    String thumbnail = heroData != null ? heroData.portrait() : "";

    if (title.isBlank()) {
      return EmbedCreateSpec.builder()
          .title(i18nManager.localize("hero.unknown_hero_title", currentLocale))
          .description(i18nManager.localize("hero.unknown_hero_description", currentLocale))
          .color(EmbedUtils.ERROR_COLOR)
          .build();
    }

    EmbedCreateSpec.Builder embedBuilder =
        EmbedCreateSpec.builder().title(title).color(EmbedUtils.DEFAULT_COLOR);

    if (!StringUtils.isBlank(thumbnail)) embedBuilder.thumbnail(thumbnail);
    if (!StringUtils.isBlank(heroData.description()))
      embedBuilder.description(heroData.description());

    if (!StringUtils.isBlank(heroData.role()))
      embedBuilder.addField(
          i18nManager.localize("hero.role", currentLocale),
          WordUtils.capitalizeFully(heroData.role()),
          true);

    if (!StringUtils.isBlank(heroData.subrole()))
      embedBuilder.addField(
          i18nManager.localize("hero.sub_role", currentLocale),
          WordUtils.capitalizeFully(heroData.subrole()),
          true);

    if (heroData.hitpoints() != null)
      embedBuilder.addField(
          i18nManager.localize("hero.hitpoints", currentLocale),
          formatHitPoints(heroData.hitpoints(), currentLocale),
          true);

    if (!StringUtils.isBlank(heroData.location()))
      embedBuilder.addField(
          i18nManager.localize("hero.location", currentLocale),
          WordUtils.capitalizeFully(heroData.location()),
          true);

    if (heroData.age() != null)
      embedBuilder.addField(
          i18nManager.localize("hero.age", currentLocale), String.valueOf(heroData.age()), true);

    if (!StringUtils.isBlank(heroData.birthday()))
      embedBuilder.addField(
          i18nManager.localize("hero.birthday", currentLocale),
          WordUtils.capitalizeFully(heroData.birthday()),
          true);

    return embedBuilder.build();
  }

  private String formatHitPoints(HitPoints hitPoints, Locale currentLocale) {
    StringBuilder hitPointsBuilder = new StringBuilder();

    if (hitPoints.health() > 0) {
      hitPointsBuilder
          .append(hitPoints.health())
          .append(" ")
          .append(i18nManager.localize("hero.hitpoints.health", currentLocale));
    }
    if (hitPoints.armor() > 0) {
      if (!hitPointsBuilder.isEmpty()) hitPointsBuilder.append(", ");
      hitPointsBuilder
          .append(hitPoints.armor())
          .append(" ")
          .append(i18nManager.localize("hero.hitpoints.armor", currentLocale));
    }
    if (hitPoints.shields() > 0) {
      if (!hitPointsBuilder.isEmpty()) hitPointsBuilder.append(", ");
      hitPointsBuilder
          .append(hitPoints.shields())
          .append(" ")
          .append(i18nManager.localize("hero.hitpoints.shields", currentLocale));
    }

    if (hitPointsBuilder.isEmpty()) {
      return String.valueOf(hitPoints.total());
    }

    return hitPoints.total() + " (" + hitPointsBuilder + ")";
  }
}
