package dev.asyncluna.zenith.discord.feature.overwatch.maps;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.asyncluna.zenith.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.Map;
import dev.asyncluna.zenith.discord.command.BotCommand;
import dev.asyncluna.zenith.discord.command.Command;
import dev.asyncluna.zenith.discord.command.CommandContext;
import dev.asyncluna.zenith.discord.command.CommandOption;
import dev.asyncluna.zenith.discord.util.DiscordConstants;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(name = "map", description = "Get a list of maps for a specific gamemode.")
@CommandOption(
    name = "gamemode",
    description = "Filter by gamemode.",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true,
    required = true)
public class MapCommand implements BotCommand {
  private final OverfastApiService overfastApiService;

  @Getter
  private final Cache<String, MapSession> sessionCache =
      Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).maximumSize(1000).build();

  public record MapSession(String userId, String gamemode, String[] selectedMapKeyHolder) {
    public MapSession(String userId, String gamemode) {
      this(userId, gamemode, new String[1]);
    }

    public String getSelectedMapKey() {
      return selectedMapKeyHolder[0];
    }

    public void setSelectedMapKey(String selectedMapKey) {
      this.selectedMapKeyHolder[0] = selectedMapKey;
    }
  }

  @Override
  public Mono<?> handle(CommandContext ctx) {
    String gamemode =
        ctx.getOptionAsString("gamemode")
            .orElseThrow()
            .toLowerCase()
            .replace("_", "-")
            .replace(" ", "-");

    String userId = ctx.getAuthor().getId().asString();
    MapSession session = new MapSession(userId, gamemode);
    String sessionId = UUID.randomUUID().toString();

    return overfastApiService
        .getMaps(gamemode)
        .collectList()
        .flatMap(
            maps -> {
              if (maps.isEmpty()) {
                return ctx.editReply(ctx.localize("map.no_results")).then();
              }

              Map firstMap = maps.getFirst();
              session.setSelectedMapKey(firstMap.key().toLowerCase());
              sessionCache.put(sessionId, session);

              EmbedCreateSpec embed = createMapEmbed(firstMap, ctx);

              if (maps.size() <= DiscordConstants.MAX_COMPONENT_ENTRIES) {
                SelectMenu selectMenu =
                    SelectMenu.of(
                            "map-1:" + sessionId,
                            maps.stream()
                                .map(map -> SelectMenu.Option.of(map.name(), map.key()))
                                .toList())
                        .withPlaceholder(
                            String.format(
                                ctx.localize("map.select_a_map"),
                                maps.getFirst().name(),
                                maps.getLast().name()));

                return ctx.editReply().withEmbeds(embed).withComponents(ActionRow.of(selectMenu));
              }

              List<Map> firstHalf =
                  maps.stream().limit(DiscordConstants.MAX_COMPONENT_ENTRIES).toList();
              List<Map> secondHalf =
                  maps.stream()
                      .skip(DiscordConstants.MAX_COMPONENT_ENTRIES)
                      .limit(DiscordConstants.MAX_COMPONENT_ENTRIES)
                      .toList();

              String placeholderOne =
                  String.format(
                      ctx.localize("map.select_a_map"),
                      firstHalf.getFirst().name(),
                      firstHalf.getLast().name());
              String placeholderTwo =
                  String.format(
                      ctx.localize("map.select_a_map"),
                      secondHalf.getFirst().name(),
                      secondHalf.getLast().name());

              SelectMenu selectMenuOne =
                  SelectMenu.of(
                          "map-1:" + sessionId,
                          firstHalf.stream()
                              .map(map -> SelectMenu.Option.of(map.name(), map.key()))
                              .toList())
                      .withPlaceholder(placeholderOne);

              SelectMenu selectMenuTwo =
                  SelectMenu.of(
                          "map-2:" + sessionId,
                          secondHalf.stream()
                              .map(map -> SelectMenu.Option.of(map.name(), map.key()))
                              .toList())
                      .withPlaceholder(placeholderTwo);

              return ctx.editReply()
                  .withEmbeds(embed)
                  .withComponents(ActionRow.of(selectMenuOne), ActionRow.of(selectMenuTwo));
            });
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
        optionName.equals("gamemode")
            ? overfastApiService
                .getMaps()
                .flatMapIterable(Map::gamemodes)
                .distinct()
                .filter(gamemode -> gamemode.toLowerCase().contains(userInput))
                .take(DiscordConstants.MAX_AUTO_COMPLETE_RESULTS)
                .map(
                    gamemode ->
                        (ApplicationCommandOptionChoiceData)
                            ApplicationCommandOptionChoiceData.builder()
                                .name(WordUtils.capitalizeFully(gamemode.replace("-", " ")))
                                .value(gamemode)
                                .build())
                .collectList()
            : Mono.just(Collections.emptyList());

    return choicesMono.flatMap(event::respondWithSuggestions).then();
  }

  private EmbedCreateSpec createMapEmbed(Map map, CommandContext ctx) {
    EmbedCreateSpec.Builder embedBuilder =
        EmbedCreateSpec.builder().title(map.name()).color(EmbedUtils.DEFAULT_COLOR);

    if (!StringUtils.isBlank(map.location()))
      embedBuilder.addField(ctx.localize("map.location"), map.location(), true);

    if (!StringUtils.isBlank(map.countryCode()))
      embedBuilder.addField(
          ctx.localize("map.country_code"), map.countryCode().toUpperCase(), true);

    if (map.gamemodes() != null && !map.gamemodes().isEmpty()) {
      String modesList =
          map.gamemodes().stream()
              .map(mode -> WordUtils.capitalizeFully(mode.replace("-", " ")))
              .collect(Collectors.joining(", "));
      embedBuilder.addField(ctx.localize("map.gamemodes"), modesList, false);
    }

    if (!StringUtils.isBlank(map.screenshot())) embedBuilder.image(map.screenshot());

    return embedBuilder.build();
  }
}
