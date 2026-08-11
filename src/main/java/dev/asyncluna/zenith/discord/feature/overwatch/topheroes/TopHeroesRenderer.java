package dev.asyncluna.zenith.discord.feature.overwatch.topheroes;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.PlayerCareerStats;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import discord4j.core.spec.EmbedCreateSpec;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.WordUtils;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class TopHeroesRenderer {
  public static final int PAGE_SIZE = 10;

  private final I18nManager i18nManager;

  public EmbedCreateSpec render(TopHeroesSession session, Locale locale) {
    TopHeroesCommand.Stat stat = TopHeroesCommand.findStat(session.statPath());
    List<Map.Entry<String, JsonNode>> ranked = ranked(session.stats(), stat);
    int start = Math.min(session.page() * PAGE_SIZE, ranked.size());
    int end = Math.min(start + PAGE_SIZE, ranked.size());
    List<Map.Entry<String, JsonNode>> page = ranked.subList(start, end);

    EmbedCreateSpec.Builder builder =
        EmbedCreateSpec.builder()
            .title(i18nManager.localize("top_heroes.title", locale, session.playerId()))
            .description(
                i18nManager.localize(
                    "top_heroes.description",
                    locale,
                    i18nManager.localize(
                        session.mode().equals("quickplay")
                            ? "top_heroes.mode.quickplay"
                            : "top_heroes.mode.competitive",
                        locale),
                    i18nManager.localize("top_heroes.stat." + stat.path(), locale)))
            .color(EmbedUtils.DEFAULT_COLOR);

    if (ranked.isEmpty()) {
      return builder
          .addField(
              i18nManager.localize("top_heroes.no_results.title", locale),
              i18nManager.localize("top_heroes.no_results.description", locale),
              false)
          .build();
    }

    for (int i = 0; i < page.size(); i++) {
      Map.Entry<String, JsonNode> hero = page.get(i);
      builder.addField(
          (start + i + 1)
              + ". "
              + WordUtils.capitalizeFully(hero.getKey().replace('_', ' ').replace('-', ' ')),
          format(value(hero.getValue(), stat), stat),
          false);
    }
    return builder.build();
  }

  public int totalPages(TopHeroesSession session) {
    return Math.max(
        1,
        (ranked(session.stats(), TopHeroesCommand.findStat(session.statPath())).size()
                + PAGE_SIZE
                - 1)
            / PAGE_SIZE);
  }

  private static List<Map.Entry<String, JsonNode>> ranked(
      PlayerCareerStats stats, TopHeroesCommand.Stat stat) {
    return stats.heroes().entrySet().stream()
        .filter(entry -> !entry.getKey().equalsIgnoreCase("all-heroes"))
        .filter(entry -> value(entry.getValue(), stat) != null)
        .sorted(
            Comparator.comparingDouble(
                    (Map.Entry<String, JsonNode> e) ->
                        numericValue(Objects.requireNonNull(value(e.getValue(), stat))))
                .reversed())
        .toList();
  }

  private static JsonNode value(JsonNode hero, TopHeroesCommand.Stat stat) {
    JsonNode node = hero;
    for (String part : stat.path().split("\\.")) node = node == null ? null : node.path(part);
    return node == null || node.isMissingNode() || node.isNull() ? null : node;
  }

  private static double numericValue(JsonNode node) {
    if (node.isNumber()) return node.doubleValue();
    try {
      return Double.parseDouble(node.asString().replace(",", ""));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static String format(JsonNode node, TopHeroesCommand.Stat stat) {
    if (stat.duration() && node.isNumber()) {
      long seconds = node.longValue();
      return String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }
    return node.asString();
  }
}
