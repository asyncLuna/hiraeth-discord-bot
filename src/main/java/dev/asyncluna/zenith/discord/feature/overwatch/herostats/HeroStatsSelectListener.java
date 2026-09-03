package dev.asyncluna.zenith.discord.feature.overwatch.herostats;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.core.integration.overfastapi.OverfastApiService;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.HeroShort;
import dev.asyncluna.zenith.core.integration.overfastapi.dto.HeroStatsSummary;
import dev.asyncluna.zenith.discord.feature.overwatch.herostats.HeroStatsCommand.HeroStatsSession;
import dev.asyncluna.zenith.discord.listener.EventListener;
import dev.asyncluna.zenith.discord.settings.GuildSettingsProvider;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.LayoutComponent;
import discord4j.core.spec.EmbedCreateSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.WordUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class HeroStatsSelectListener implements EventListener<SelectMenuInteractionEvent> {
    private final OverfastApiService overfastApiService;
    private final GuildSettingsProvider guildSettingsProvider;
    private final I18nManager i18nManager;
    private final HeroStatsCommand heroStatsCommand;

    @Override
    public Mono<Void> execute(SelectMenuInteractionEvent event) {
        String fullCustomId = event.getCustomId();

        String[] parts = fullCustomId.split(":", 2);
        String menuPrefix = parts[0];

        if (!"hs-1".equals(menuPrefix) && !"hs-2".equals(menuPrefix)) return Mono.empty();
        if (parts.length < 2) return Mono.empty();

        String sessionId = parts[1];

        HeroStatsSession session = heroStatsCommand.getSessionCache().getIfPresent(sessionId);
        String guildIdStr =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");

        return guildSettingsProvider
                .get(guildIdStr)
                .flatMap(settings -> {
                    Locale currentLocale =
                            SupportedLocale.forLanguageTag(settings.getLocale()).getLocale();

                    if (session == null) {
                        return event.reply()
                                .withEphemeral(true)
                                .withContent(i18nManager.localize(
                                        "error.menu_interaction_expired", currentLocale, "hero_stats"));
                    }

                    String interactionUserId =
                            event.getInteraction().getUser().getId().asString();
                    if (!session.userId().equals(interactionUserId)) {
                        String localizedError = i18nManager.localize("error.menu_not_owned", currentLocale);
                        return event.reply().withEphemeral(true).withContent(localizedError);
                    }

                    String selectedHeroName = event.getValues().getFirst();
                    session.setSelectedHeroKey(selectedHeroName.toLowerCase());

                    Mono<Map<String, HeroShort>> heroesMapMono = overfastApiService
                            .getHeroes(null, null, null)
                            .collect(Collectors.toMap(hero -> hero.key().toLowerCase(), hero -> hero));

                    Mono<HeroStatsSummary> statsMono = overfastApiService
                            .getHeroStats(
                                    session.platform(),
                                    session.gamemode(),
                                    session.region(),
                                    session.role(),
                                    session.map(),
                                    session.competitiveDivision(),
                                    session.orderBy())
                            .filter(stats -> stats.hero().equalsIgnoreCase(selectedHeroName))
                            .next();

                    return Mono.zip(statsMono, heroesMapMono).flatMap(tuple -> {
                        HeroStatsSummary heroStats = tuple.getT1();
                        Map<String, HeroShort> heroesMap = tuple.getT2();

                        HeroShort heroDetails = heroesMap.get(heroStats.hero().toLowerCase());
                        String title = heroDetails != null ? heroDetails.name() : heroStats.hero();
                        String thumbnail = heroDetails != null ? heroDetails.portrait() : null;

                        EmbedCreateSpec.Builder updatedEmbedBuilder =
                                EmbedCreateSpec.builder().title(title).color(EmbedUtils.DEFAULT_COLOR);

                        String heroRole = heroDetails != null ? heroDetails.role() : null;
                        if (heroRole != null && !heroRole.isBlank()) {
                            updatedEmbedBuilder.addField(
                                    i18nManager.localize("hero.role", currentLocale),
                                    WordUtils.capitalizeFully(heroRole),
                                    true);
                        }

                        updatedEmbedBuilder
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

                        if (session.map() != null)
                            updatedEmbedBuilder.addField(
                                    i18nManager.localize("hero.map", currentLocale),
                                    WordUtils.capitalizeFully(session.map()),
                                    true);

                        if (session.competitiveDivision() != null)
                            updatedEmbedBuilder.addField(
                                    i18nManager.localize("hero.competitive_division", currentLocale),
                                    session.competitiveDivision().getFriendlyName(),
                                    true);

                        if (session.orderBy() != null)
                            updatedEmbedBuilder.addField(
                                    i18nManager.localize("hero.order_by", currentLocale),
                                    session.orderBy().getFriendlyName(),
                                    true);

                        updatedEmbedBuilder
                                .addField(
                                        i18nManager.localize("hero.pickrate", currentLocale),
                                        (heroStats.pickrate() != null ? heroStats.pickrate() + "%" : "N/A"),
                                        true)
                                .addField(
                                        i18nManager.localize("hero.winrate", currentLocale),
                                        (heroStats.winrate() != null ? heroStats.winrate() + "%" : "N/A"),
                                        true)
                                .addField(
                                        i18nManager.localize("hero.banrate", currentLocale),
                                        (heroStats.banrate() != null ? heroStats.banrate() + "%" : "N/A"),
                                        true);

                        if (thumbnail != null) updatedEmbedBuilder.thumbnail(thumbnail);

                        String viewDetailsLabel = i18nManager.localize("hero.view_details", currentLocale);
                        Button detailsButton = Button.primary("hs-btn:" + sessionId, viewDetailsLabel);

                        List<LayoutComponent> layoutComponents = new ArrayList<>();
                        event.getMessage()
                                .ifPresent(message -> message.getComponents().forEach(row -> {
                                    if (row instanceof ActionRow actionRow) {
                                        boolean hasButton = actionRow.getChildren().stream()
                                                .anyMatch(messageComponent -> messageComponent instanceof Button);
                                        if (hasButton) layoutComponents.add(ActionRow.of(detailsButton));
                                        else layoutComponents.add(actionRow);
                                    }
                                }));

                        return event.edit()
                                .withEmbeds(updatedEmbedBuilder.build())
                                .withComponents(layoutComponents);
                    });
                })
                .then();
    }
}
