package dev.asyncluna.owbot.discord.command.commands;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.asyncluna.owbot.core.i18n.I18nManager;
import dev.asyncluna.owbot.core.i18n.SupportedLocale;
import dev.asyncluna.owbot.core.integration.overfastapi.OverfastApiQueryParam;
import dev.asyncluna.owbot.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.owbot.core.integration.overfastapi.dto.Hero;
import dev.asyncluna.owbot.core.integration.overfastapi.dto.HitPoints;
import dev.asyncluna.owbot.core.model.GuildSettings;
import dev.asyncluna.owbot.discord.command.BotCommand;
import dev.asyncluna.owbot.discord.command.Command;
import dev.asyncluna.owbot.discord.command.CommandContext;
import dev.asyncluna.owbot.discord.command.CommandOption;
import dev.asyncluna.owbot.discord.util.EmbedUtils;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(name = "hero_data", description = "Get stats for a specific hero.")
@CommandOption(
    name = "hero",
    description = "Filter by hero.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true,
    required = true)
@CommandOption(
    name = "locale",
    description = "Translation locale.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true)
public class HeroDataCommand implements BotCommand {
  private final OverfastApiService overfastApiService;

  @Getter
  private final Cache<String, HeroDataSession> sessionCache =
      Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).maximumSize(1000).build();

  public record HeroDataSession(
      String userId, String heroKey, OverfastApiQueryParam.Locale locale) {}

  @Override
  public Mono<Void> handle(CommandContext ctx) {
    String heroKey = ctx.getOptionAsString("hero").orElseThrow().toLowerCase();
    OverfastApiQueryParam.Locale locale =
        ctx.getOptionAsString("locale").map(OverfastApiQueryParam.Locale::fromApiName).orElse(null);

    String userId = ctx.getAuthor().getId().asString();

    HeroDataSession session = new HeroDataSession(userId, heroKey, locale);
    String sessionId = UUID.randomUUID().toString();
    sessionCache.put(sessionId, session);

    return overfastApiService
        .getHeroData(heroKey, locale)
        .flatMap(
            heroData -> {
              EmbedCreateSpec embed =
                  createHeroDataEmbed(
                      heroData, locale, ctx.getGuildSettings(), ctx.getI18nManager());

              String viewAbilitiesLabel = ctx.localize("hero.view_abilities");
              Button abilitiesButton =
                  Button.primary("hd-abilities:" + sessionId, viewAbilitiesLabel);

              String viewPerksLabel = ctx.localize("hero.view_perks");
              Button perksButton = Button.secondary("hd-perks:" + sessionId, viewPerksLabel);

              return ctx.editReply()
                  .withEmbeds(embed)
                  .withComponents(ActionRow.of(abilitiesButton, perksButton));
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
          case "hero" ->
              overfastApiService
                  .getHeroes()
                  .filter(hero -> hero.name().toLowerCase().contains(userInput))
                  .map(
                      hero ->
                          (ApplicationCommandOptionChoiceData)
                              ApplicationCommandOptionChoiceData.builder()
                                  .name(hero.name())
                                  .value(hero.key())
                                  .build())
                  .take(25)
                  .collectList();
          case "locale" ->
              Mono.just(OverfastApiQueryParam.Locale.values())
                  .flatMapMany(Flux::fromArray)
                  .filter(locale -> locale.getFriendlyName().toLowerCase().contains(userInput))
                  .map(
                      locale ->
                          (ApplicationCommandOptionChoiceData)
                              ApplicationCommandOptionChoiceData.builder()
                                  .name(locale.getFriendlyName())
                                  .value(locale.toString())
                                  .build())
                  .take(25)
                  .collectList();
          default -> Mono.just(Collections.emptyList());
        };

    return choicesMono.flatMap(event::respondWithSuggestions).then();
  }

  public EmbedCreateSpec createHeroDataEmbed(
      Hero heroData,
      OverfastApiQueryParam.Locale locale,
      GuildSettings guildSettings,
      I18nManager i18nManager) {
    Locale currentLocale = SupportedLocale.forLanguageTag(guildSettings.getLocale()).getLocale();
    String title = heroData != null ? heroData.name() : "";
    String thumbnail = heroData != null ? heroData.portrait() : "";

    if (title.isBlank())
      return EmbedCreateSpec.builder()
          .title(i18nManager.localize("hero.unknown_hero_title", currentLocale))
          .description(i18nManager.localize("hero.unknown_hero_description", currentLocale))
          .color(EmbedUtils.ERROR_COLOR)
          .build();

    EmbedCreateSpec.Builder embedBuilder =
        EmbedCreateSpec.builder().title(title).color(EmbedUtils.DEFAULT_COLOR);

    if (!StringUtils.isBlank(thumbnail)) embedBuilder.thumbnail(thumbnail);

    if (!StringUtils.isBlank(heroData.description()))
      embedBuilder.description(heroData.description());

    if (!StringUtils.isBlank(heroData.role()))
      embedBuilder.addField(
          i18nManager.localize("hero.role", currentLocale),
          StringUtils.capitalize(heroData.role()),
          true);

    if (!StringUtils.isBlank(heroData.subrole()))
      embedBuilder.addField(
          i18nManager.localize("hero.sub_role", currentLocale),
          StringUtils.capitalize(heroData.subrole()),
          true);

    if (heroData.hitpoints() != null)
      embedBuilder.addField(
          i18nManager.localize("hero.hitpoints", currentLocale),
          formatHitPoints(heroData.hitpoints(), guildSettings, i18nManager),
          true);

    if (!StringUtils.isBlank(heroData.location()))
      embedBuilder.addField(
          i18nManager.localize("hero.location", currentLocale),
          StringUtils.capitalize(heroData.location()),
          true);

    if (heroData.age() != null)
      embedBuilder.addField(
          i18nManager.localize("hero.age", currentLocale), String.valueOf(heroData.age()), true);

    if (!StringUtils.isBlank(heroData.birthday()))
      embedBuilder.addField(
          i18nManager.localize("hero.birthday", currentLocale),
          StringUtils.capitalize(heroData.birthday()),
          true);

    if (locale != null)
      embedBuilder.footer(
          i18nManager.localize("locale", currentLocale, locale) + ": " + locale.getFriendlyName(),
          null);

    return embedBuilder.build();
  }

  public String formatHitPoints(
      HitPoints hitPoints, GuildSettings guildSettings, I18nManager i18nManager) {
    Locale currentLocale = SupportedLocale.forLanguageTag(guildSettings.getLocale()).getLocale();
    StringBuilder hitPointsBuilder = new StringBuilder();

    if (hitPoints.health() > 0)
      hitPointsBuilder
          .append(hitPoints.health())
          .append(" ")
          .append(i18nManager.localize("hero.hitpoints.health", currentLocale));
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

    if (hitPointsBuilder.isEmpty()) return String.valueOf(hitPoints.total());

    return hitPoints.total() + " (" + hitPointsBuilder + ")";
  }
}
