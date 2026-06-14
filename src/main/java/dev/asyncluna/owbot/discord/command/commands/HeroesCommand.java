package dev.asyncluna.owbot.discord.command.commands;

import dev.asyncluna.owbot.core.integration.overfastapi.OverfastApiQueryParam;
import dev.asyncluna.owbot.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.owbot.core.integration.overfastapi.dto.HeroShort;
import dev.asyncluna.owbot.discord.command.BotCommand;
import dev.asyncluna.owbot.discord.command.Command;
import dev.asyncluna.owbot.discord.command.CommandContext;
import dev.asyncluna.owbot.discord.command.CommandOption;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Color;
import io.sentry.util.StringUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(name = "heroes", description = "Get a list of Overwatch heroes.")
@CommandOption(
    name = "role",
    description = "Filter by role.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true)
@CommandOption(
    name = "locale",
    description = "Translation locale.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true)
@CommandOption(
    name = "gamemode",
    description = "Filter by gamemode.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true)
public class HeroesCommand implements BotCommand {
  private final OverfastApiService overfastApiService;

  @Override
  public Mono<Void> handle(CommandContext ctx) {
    OverfastApiQueryParam.Role role =
        ctx.getOptionAsString("role").map(OverfastApiQueryParam.Role::valueOf).orElse(null);
    OverfastApiQueryParam.Locale locale =
        ctx.getOptionAsString("locale").map(OverfastApiQueryParam.Locale::valueOf).orElse(null);
    OverfastApiQueryParam.Gamemode gamemode =
        ctx.getOptionAsString("gamemode").map(OverfastApiQueryParam.Gamemode::valueOf).orElse(null);

    String userId = ctx.getEvent().getInteraction().getUser().getId().asString();
    String stateContext =
        String.format("owner=%s&role=%s&locale=%s&gamemode=%s", userId, role, locale, gamemode);

    return overfastApiService
        .getHeroes(role, locale, gamemode)
        .collectList()
        .flatMap(
            heroes -> {
              if (heroes.isEmpty()) return ctx.editReply(ctx.localize("heroes.no_results")).then();

              HeroShort firstHero = heroes.getFirst();

              if (heroes.size() <= 25) {
                SelectMenu selectMenu =
                    SelectMenu.of(
                            "hero-select-1:" + stateContext,
                            heroes.stream()
                                .map(hero -> SelectMenu.Option.of(hero.name(), hero.key()))
                                .toList())
                        .withPlaceholder(
                            String.format(
                                ctx.localize("heroes.select_a_hero"),
                                heroes.getFirst().name(),
                                heroes.getLast().name()));

                return ctx.getEvent()
                    .editReply()
                    .withEmbeds(createHeroEmbed(firstHero, ctx))
                    .withComponents(ActionRow.of(selectMenu));
              }

              List<HeroShort> firstHalf = heroes.stream().limit(25).toList();
              List<HeroShort> secondHalf = heroes.stream().skip(25).limit(25).toList();

              String labelOne =
                  String.format(
                      ctx.localize("heroes.select_a_hero"),
                      firstHalf.getFirst().name(),
                      firstHalf.getLast().name());
              String labelTwo =
                  String.format(
                      ctx.localize("heroes.select_a_hero"),
                      secondHalf.getFirst().name(),
                      secondHalf.getLast().name());

              SelectMenu selectMenuOne =
                  SelectMenu.of(
                          "hero-select-1:" + stateContext,
                          firstHalf.stream()
                              .map(hero -> SelectMenu.Option.of(hero.name(), hero.key()))
                              .toList())
                      .withPlaceholder(labelOne);

              SelectMenu selectMenuTwo =
                  SelectMenu.of(
                          "hero-select-2:" + stateContext,
                          secondHalf.stream()
                              .map(hero -> SelectMenu.Option.of(hero.name(), hero.key()))
                              .toList())
                      .withPlaceholder(labelTwo);

              return ctx.getEvent()
                  .editReply()
                  .withEmbeds(createHeroEmbed(firstHero, ctx))
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

    List<ApplicationCommandOptionChoiceData> choices =
        switch (optionName) {
          case "role" ->
              Arrays.stream(OverfastApiQueryParam.Role.values())
                  .filter(role -> role.name().toLowerCase().contains(userInput))
                  .map(
                      role ->
                          (ApplicationCommandOptionChoiceData)
                              ApplicationCommandOptionChoiceData.builder()
                                  .name(StringUtils.capitalize(role.toString()))
                                  .value(role.name())
                                  .build())
                  .toList();
          case "locale" ->
              Arrays.stream(OverfastApiQueryParam.Locale.values())
                  .filter(locale -> locale.name().toLowerCase().contains(userInput))
                  .map(
                      locale ->
                          (ApplicationCommandOptionChoiceData)
                              ApplicationCommandOptionChoiceData.builder()
                                  .name(locale.toString())
                                  .value(locale.name())
                                  .build())
                  .toList();
          case "gamemode" ->
              Arrays.stream(OverfastApiQueryParam.Gamemode.values())
                  .filter(gamemode -> gamemode.name().toLowerCase().contains(userInput))
                  .map(
                      gamemode ->
                          (ApplicationCommandOptionChoiceData)
                              ApplicationCommandOptionChoiceData.builder()
                                  .name(StringUtils.capitalize(gamemode.toString()))
                                  .value(gamemode.name())
                                  .build())
                  .toList();
          default -> Collections.emptyList();
        };

    return event.respondWithSuggestions(choices).then();
  }

  private EmbedCreateSpec createHeroEmbed(HeroShort hero, CommandContext ctx) {
    boolean availableInStadium = hero.gamemodes().stream().anyMatch("stadium"::equalsIgnoreCase);
    String stadiumStatus =
        availableInStadium ? "✅ " + ctx.localize("yes") : "❌ " + ctx.localize("no");

    return EmbedCreateSpec.builder()
        .title(hero.name())
        .thumbnail(hero.portrait())
        .color(Color.of(255, 156, 0))
        .addField(ctx.localize("heroes.role"), StringUtils.capitalize(hero.role()), true)
        .addField(ctx.localize("heroes.sub_role"), StringUtils.capitalize(hero.subrole()), true)
        .addField(ctx.localize("heroes.available_in_stadium"), stadiumStatus, true)
        .build();
  }
}
