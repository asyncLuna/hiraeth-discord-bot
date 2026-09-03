package dev.asyncluna.zenith.discord.feature.overwatch.topheroes;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TopHeroesComponentFactory {
    private final I18nManager i18nManager;
    private final TopHeroesRenderer renderer;

    public ActionRow createPageButtons(String sessionId, TopHeroesSession session, Locale locale) {
        int totalPages = renderer.totalPages(session);
        Button previous = Button.secondary("th-prev:" + sessionId, i18nManager.localize("top_heroes.previous", locale))
                .disabled(session.page() == 0);
        Button next = Button.secondary("th-next:" + sessionId, i18nManager.localize("top_heroes.next", locale))
                .disabled(session.page() >= totalPages - 1);
        return ActionRow.of(previous, next);
    }
}
