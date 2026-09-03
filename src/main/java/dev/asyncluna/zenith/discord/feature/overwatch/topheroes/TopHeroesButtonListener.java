package dev.asyncluna.zenith.discord.feature.overwatch.topheroes;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.discord.listener.EventListener;
import dev.asyncluna.zenith.discord.settings.GuildSettingsProvider;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TopHeroesButtonListener implements EventListener<ButtonInteractionEvent> {
    private final TopHeroesSessionManager sessionManager;
    private final TopHeroesRenderer renderer;
    private final TopHeroesComponentFactory componentFactory;
    private final GuildSettingsProvider guildSettingsProvider;
    private final I18nManager i18nManager;

    @Override
    public Mono<Void> execute(ButtonInteractionEvent event) {
        String[] parts = event.getCustomId().split(":", 2);
        if (parts.length < 2 || (!parts[0].equals("th-prev") && !parts[0].equals("th-next"))) return Mono.empty();

        String sessionId = parts[1];
        TopHeroesSession session = sessionManager.get(sessionId);
        String guildId =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");

        return guildSettingsProvider.get(guildId).flatMap(settings -> {
            Locale locale = SupportedLocale.forLanguageTag(settings.getLocale()).getLocale();

            if (session == null)
                return event.reply()
                        .withEphemeral(true)
                        .withContent(i18nManager.localize("error.menu_interaction_expired", locale, "top_heroes"));

            if (!session.userId()
                    .equals(event.getInteraction().getUser().getId().asString()))
                return event.reply()
                        .withEphemeral(true)
                        .withContent(i18nManager.localize("error.menu_not_owned", locale));

            if (parts[0].equals("th-prev") && session.page() > 0) session.previousPage();
            if (parts[0].equals("th-next") && session.page() < renderer.totalPages(session) - 1) session.nextPage();

            return event.edit()
                    .withEmbeds(renderer.render(session, locale))
                    .withComponents(componentFactory.createPageButtons(sessionId, session, locale))
                    .then();
        });
    }
}
