package dev.asyncluna.owbot.discord.command.commands;

import dev.asyncluna.owbot.core.integration.overfastapi.OverfastApiQueryParam;
import dev.asyncluna.owbot.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.owbot.core.integration.overfastapi.dto.HeroShort;
import dev.asyncluna.owbot.core.integration.overfastapi.dto.HeroStatsSummary;
import dev.asyncluna.owbot.discord.command.BotCommand;
import dev.asyncluna.owbot.discord.command.Command;
import dev.asyncluna.owbot.discord.command.CommandContext;
import dev.asyncluna.owbot.discord.command.CommandOption;
import dev.asyncluna.owbot.discord.util.DiscordConstants;
import dev.asyncluna.owbot.discord.util.EmbedUtils;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import io.sentry.util.StringUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(name = "hero_stats", description = "Get stats for a specific hero.")
@CommandOption(
    name = "platform",
    description = "Filter by platform.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true,
    required = true)
@CommandOption(
    name = "gamemode",
    description = "Filter by gamemode.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true,
    required = true)
@CommandOption(
    name = "region",
    description = "Filter by region.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true,
    required = true)
@CommandOption(
    name = "role",
    description = "Filter by role.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true)
@CommandOption(
    name = "map",
    description = "Filter by map.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true)
@CommandOption(
    name = "competitive_division",
    description = "Filter by competitive division.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true)
@CommandOption(
    name = "order_by",
    description = "Order by a specific stat.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true)
public class HeroStatsCommand implements BotCommand {
  private final OverfastApiService overfastApiService;

  @Override
  public Mono<Void> handle(CommandContext ctx) {
    OverfastApiQueryParam.Platform platform =
        ctx.getOptionAsString("platform")
            .map(OverfastApiQueryParam.Platform::fromApiName)
            .orElseThrow();
    String gamemode = ctx.getOptionAsString("gamemode").orElseThrow();
    OverfastApiQueryParam.Region region =
        ctx.getOptionAsString("region")
            .map(OverfastApiQueryParam.Region::fromApiName)
            .orElseThrow();
    OverfastApiQueryParam.Role role =
        ctx.getOptionAsString("role").map(OverfastApiQueryParam.Role::fromApiName).orElse(null);
    String map = ctx.getOptionAsString("map").orElse(null);
    OverfastApiQueryParam.CompetitiveDivision competitiveDivision =
        ctx.getOptionAsString("competitive_division")
            .map(OverfastApiQueryParam.CompetitiveDivision::fromApiName)
            .orElse(null);
    OverfastApiQueryParam.OrderBy orderBy =
        ctx.getOptionAsString("order_by")
            .map(OverfastApiQueryParam.OrderBy::fromApiName)
            .orElse(null);

    String userId = ctx.getEvent().getUser().getId().asString();

    StringBuilder stateBuilder = new StringBuilder();
    stateBuilder.append("o=").append(userId);
    stateBuilder.append("&p=").append(platform.name());
    stateBuilder.append("&g=").append(gamemode);
    stateBuilder.append("&reg=").append(region.name());

    if (role != null) stateBuilder.append("&rol=").append(role.name());
    if (map != null) stateBuilder.append("&m=").append(map);
    if (competitiveDivision != null) stateBuilder.append("&c=").append(competitiveDivision.name());
    if (orderBy != null) stateBuilder.append("&ord=").append(orderBy.name());

    String stateContext = stateBuilder.toString();

    Mono<Map<String, HeroShort>> heroesMapMono =
        overfastApiService
            .getHeroes()
            .collect(Collectors.toMap(hero -> hero.key().toLowerCase(), hero -> hero));

    Mono<List<HeroStatsSummary>> statsMono =
        overfastApiService
            .getHeroStats(platform, gamemode, region, role, map, competitiveDivision, orderBy)
            .collectList();

    return Mono.zip(statsMono, heroesMapMono)
        .flatMap(
            tuple -> {
              List<HeroStatsSummary> heroStats = tuple.getT1();
              Map<String, HeroShort> heroesMap = tuple.getT2();

              if (heroStats.isEmpty())
                return ctx.editReply(ctx.localize("hero_stats.no_results")).then();

              HeroStatsSummary firstHeroStats = heroStats.getFirst();
              HeroShort firstHeroDetails = heroesMap.get(firstHeroStats.hero().toLowerCase());

              if (heroStats.size() <= DiscordConstants.MAX_COMPONENT_ENTRIES) {
                SelectMenu selectMenu =
                    SelectMenu.of(
                            "hs-1:" + stateContext,
                            heroStats.stream()
                                .map(
                                    heroStat -> {
                                      HeroShort details =
                                          heroesMap.get(heroStat.hero().toLowerCase());
                                      String label =
                                          details != null ? details.name() : heroStat.hero();
                                      return SelectMenu.Option.of(label, heroStat.hero());
                                    })
                                .toList())
                        .withPlaceholder(
                            String.format(
                                ctx.localize("hero_stats.select_a_hero"),
                                heroesMap.containsKey(heroStats.getFirst().hero().toLowerCase())
                                    ? heroesMap
                                        .get(heroStats.getFirst().hero().toLowerCase())
                                        .name()
                                    : heroStats.getFirst().hero(),
                                heroesMap.containsKey(heroStats.getLast().hero().toLowerCase())
                                    ? heroesMap.get(heroStats.getLast().hero().toLowerCase()).name()
                                    : heroStats.getLast().hero()));

                return ctx.getEvent()
                    .editReply()
                    .withEmbeds(
                        createHeroStatsEmbed(
                            firstHeroStats,
                            firstHeroDetails,
                            platform,
                            gamemode,
                            region,
                            role,
                            map,
                            competitiveDivision,
                            orderBy,
                            ctx))
                    .withComponents(ActionRow.of(selectMenu));
              }

              List<HeroStatsSummary> firstHalf =
                  heroStats.stream().limit(DiscordConstants.MAX_COMPONENT_ENTRIES).toList();
              List<HeroStatsSummary> secondHalf =
                  heroStats.stream()
                      .skip(DiscordConstants.MAX_COMPONENT_ENTRIES)
                      .limit(DiscordConstants.MAX_COMPONENT_ENTRIES)
                      .toList();

              String labelOne =
                  String.format(
                      ctx.localize("hero_stats.select_a_hero"),
                      heroesMap.containsKey(firstHalf.getFirst().hero().toLowerCase())
                          ? heroesMap.get(firstHalf.getFirst().hero().toLowerCase()).name()
                          : firstHalf.getFirst().hero(),
                      heroesMap.containsKey(firstHalf.getLast().hero().toLowerCase())
                          ? heroesMap.get(firstHalf.getLast().hero().toLowerCase()).name()
                          : firstHalf.getLast().hero());
              String labelTwo =
                  String.format(
                      ctx.localize("hero_stats.select_a_hero"),
                      heroesMap.containsKey(secondHalf.getFirst().hero().toLowerCase())
                          ? heroesMap.get(secondHalf.getFirst().hero().toLowerCase()).name()
                          : secondHalf.getFirst().hero(),
                      heroesMap.containsKey(secondHalf.getLast().hero().toLowerCase())
                          ? heroesMap.get(secondHalf.getLast().hero().toLowerCase()).name()
                          : secondHalf.getLast().hero());

              SelectMenu selectMenuOne =
                  SelectMenu.of(
                          "hs-1:" + stateContext,
                          firstHalf.stream()
                              .map(
                                  heroStat -> {
                                    HeroShort details =
                                        heroesMap.get(heroStat.hero().toLowerCase());
                                    String label =
                                        details != null ? details.name() : heroStat.hero();
                                    return SelectMenu.Option.of(label, heroStat.hero());
                                  })
                              .toList())
                      .withPlaceholder(labelOne);

              SelectMenu selectMenuTwo =
                  SelectMenu.of(
                          "hs-2:" + stateContext,
                          secondHalf.stream()
                              .map(
                                  heroStat -> {
                                    HeroShort details =
                                        heroesMap.get(heroStat.hero().toLowerCase());
                                    String label =
                                        details != null ? details.name() : heroStat.hero();
                                    return SelectMenu.Option.of(label, heroStat.hero());
                                  })
                              .toList())
                      .withPlaceholder(labelTwo);

              return ctx.getEvent()
                  .editReply()
                  .withEmbeds(
                      createHeroStatsEmbed(
                          firstHeroStats,
                          firstHeroDetails,
                          platform,
                          gamemode,
                          region,
                          role,
                          map,
                          competitiveDivision,
                          orderBy,
                          ctx))
                  .withComponents(ActionRow.of(selectMenuOne), ActionRow.of(selectMenuTwo));
            })
        .then();
  }

  @Override
  public Mono<Void> autocomplete(ChatInputAutoCompleteEvent event) {
    ApplicationCommandInteractionOption focusedOption = event.getFocusedOption();
    String optionName = focusedOption.getName();
    String userInput =
        focusedOption
            .getValue()
            .map(ApplicationCommandInteractionOptionValue::asString)
            .map(String::toLowerCase)
            .orElse("");

    Mono<List<ApplicationCommandOptionChoiceData>> choicesMono =
        switch (optionName) {
          case "platform" ->
              Mono.just(
                  Arrays.stream(OverfastApiQueryParam.Platform.values())
                      .filter(platform -> platform.name().contains(userInput))
                      .map(
                          platform ->
                              (ApplicationCommandOptionChoiceData)
                                  ApplicationCommandOptionChoiceData.builder()
                                      .name(platform.getFriendlyName())
                                      .value(platform.name())
                                      .build())
                      .toList());
          case "gamemode" ->
              Mono.just(
                  List.of(
                      ApplicationCommandOptionChoiceData.builder()
                          .name("Quick Play")
                          .value("quickplay")
                          .build(),
                      ApplicationCommandOptionChoiceData.builder()
                          .name("Competitive")
                          .value("competitive")
                          .build()));
          case "region" ->
              Mono.just(
                  Arrays.stream(OverfastApiQueryParam.Region.values())
                      .filter(region -> region.name().contains(userInput))
                      .map(
                          region ->
                              (ApplicationCommandOptionChoiceData)
                                  ApplicationCommandOptionChoiceData.builder()
                                      .name(region.getFriendlyName())
                                      .value(region.name())
                                      .build())
                      .toList());
          case "role" ->
              Mono.just(
                  Arrays.stream(OverfastApiQueryParam.Role.values())
                      .filter(role -> role.name().contains(userInput))
                      .map(
                          role ->
                              (ApplicationCommandOptionChoiceData)
                                  ApplicationCommandOptionChoiceData.builder()
                                      .name(role.getFriendlyName())
                                      .value(role.name())
                                      .build())
                      .toList());
          case "map" ->
              overfastApiService
                  .getMaps()
                  .filter(map -> map.name().toLowerCase().contains(userInput))
                  .take(DiscordConstants.MAX_AUTO_COMPLETE_RESULTS)
                  .map(
                      map ->
                          (ApplicationCommandOptionChoiceData)
                              ApplicationCommandOptionChoiceData.builder()
                                  .name(map.name())
                                  .value(map.key())
                                  .build())
                  .collectList();
          case "competitive_division" ->
              Mono.just(
                  Arrays.stream(OverfastApiQueryParam.CompetitiveDivision.values())
                      .filter(
                          competitiveDivision ->
                              competitiveDivision.name().toLowerCase().contains(userInput))
                      .map(
                          competitiveDivision ->
                              (ApplicationCommandOptionChoiceData)
                                  ApplicationCommandOptionChoiceData.builder()
                                      .name(competitiveDivision.getFriendlyName())
                                      .value(competitiveDivision.name())
                                      .build())
                      .toList());
          case "order_by" ->
              Mono.just(
                  Arrays.stream(OverfastApiQueryParam.OrderBy.values())
                      .filter(orderBy -> orderBy.name().toLowerCase().contains(userInput))
                      .map(
                          orderBy ->
                              (ApplicationCommandOptionChoiceData)
                                  ApplicationCommandOptionChoiceData.builder()
                                      .name(orderBy.getFriendlyName())
                                      .value(orderBy.toString())
                                      .build())
                      .toList());
          default -> Mono.just(Collections.emptyList());
        };

    return choicesMono.flatMap(event::respondWithSuggestions).then();
  }

  private EmbedCreateSpec createHeroStatsEmbed(
      HeroStatsSummary heroStats,
      HeroShort heroDetails,
      OverfastApiQueryParam.Platform platform,
      String gamemode,
      OverfastApiQueryParam.Region region,
      OverfastApiQueryParam.Role role,
      String map,
      OverfastApiQueryParam.CompetitiveDivision competitiveDivision,
      OverfastApiQueryParam.OrderBy orderBy,
      CommandContext ctx) {

    String title = heroDetails != null ? heroDetails.name() : heroStats.hero();
    String thumbnail = heroDetails != null ? heroDetails.portrait() : null;

    EmbedCreateSpec.Builder embedBuilder =
        EmbedCreateSpec.builder().title(title).color(EmbedUtils.DEFAULT_COLOR);

    if (role != null)
      embedBuilder.addField(
          ctx.localize("hero_stats.role"), StringUtils.capitalize(role.getFriendlyName()), true);

    if (role == null && heroDetails != null && heroDetails.role() != null)
      embedBuilder.addField(
          ctx.localize("hero_stats.role"), StringUtils.capitalize(heroDetails.role()), true);

    if (heroDetails != null && heroDetails.subrole() != null)
      embedBuilder.addField(
          ctx.localize("hero_stats.sub_role"), StringUtils.capitalize(heroDetails.subrole()), true);

    embedBuilder
        .addField(EmbedUtils.EMPTY_FIELD)
        .addField(ctx.localize("hero_stats.platform"), platform.getFriendlyName(), true)
        .addField(ctx.localize("hero_stats.gamemode"), StringUtils.capitalize(gamemode), true)
        .addField(ctx.localize("hero_stats.region"), region.getFriendlyName(), true);

    if (map != null)
      embedBuilder.addField(ctx.localize("hero_stats.map"), StringUtils.capitalize(map), true);

    if (competitiveDivision != null)
      embedBuilder.addField(
          ctx.localize("hero_stats.competitive_division"),
          competitiveDivision.getFriendlyName(),
          true);

    if (orderBy != null)
      embedBuilder.addField(ctx.localize("hero_stats.order_by"), orderBy.getFriendlyName(), true);

    embedBuilder
        .addField(EmbedUtils.EMPTY_FIELD)
        .addField(ctx.localize("hero_stats.pickrate"), heroStats.pickrate() + "%", true)
        .addField(ctx.localize("hero_stats.winrate"), heroStats.winrate() + "%", true);

    if (thumbnail != null) embedBuilder.thumbnail(thumbnail);

    return embedBuilder.build();
  }
}
