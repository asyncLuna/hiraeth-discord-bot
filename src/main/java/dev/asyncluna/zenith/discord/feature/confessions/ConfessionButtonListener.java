package dev.asyncluna.zenith.discord.feature.confessions;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.discord.listener.EventListener;
import dev.asyncluna.zenith.discord.settings.GuildSettingsProvider;
import dev.asyncluna.zenith.discord.util.DiscordConstants;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.FileUpload;
import discord4j.core.object.component.Label;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.InteractionPresentModalSpec;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfessionButtonListener implements EventListener<ButtonInteractionEvent> {
    static final String SUBMIT_BUTTON_ID = "submit_a_confession_button";
    static final String REPLY_BUTTON_PREFIX = "reply_to_confession:";

    private final GuildSettingsProvider guildSettingsProvider;
    private final I18nManager i18nManager;
    private final ConfessionCooldown confessionCooldown;

    @Override
    public Mono<Void> execute(ButtonInteractionEvent event) {
        String customId = event.getCustomId();
        if (customId.equals(SUBMIT_BUTTON_ID)) return openConfessionModal(event);
        if (customId.startsWith(REPLY_BUTTON_PREFIX)) return openReplyModal(event, customId);
        return Mono.empty();
    }

    private Mono<Void> openConfessionModal(ButtonInteractionEvent event) {

        String guildId =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");
        return guildSettingsProvider.get(guildId).flatMap(settings -> {
            Locale locale = SupportedLocale.forLanguageTag(settings.getLocale()).getLocale();
            if (!settings.isConfessionsEnabled()) {
                return event.reply()
                        .withEphemeral(true)
                        .withContent(i18nManager.localize("confessions.disabled_interaction", locale));
            }

            if (confessionCooldown.isActive(
                    guildId, event.getInteraction().getUser().getId().asString())) {
                return event.reply()
                        .withEphemeral(true)
                        .withContent(i18nManager.localize("confessions.cooldown", locale));
            }

            log.info(
                    "Presenting a modal | Modal: {} | Guild: {} | User: {} ({})",
                    "confession_modal",
                    guildId,
                    event.getInteraction().getUser().getUsername(),
                    event.getInteraction().getUser().getId().asString());

            return event.presentModal(InteractionPresentModalSpec.builder()
                    .customId("confession_modal")
                    .title(i18nManager.localize("confessions.modal.title", locale))
                    .addComponent(Label.of(
                            i18nManager.localize("confessions.modal.content", locale),
                            TextInput.paragraph(
                                            "confession_input",
                                            1,
                                            DiscordConstants.MAX_CHARACTERS_PER_MODAL_PARAGRAPH_INPUT)
                                    .placeholder(
                                            i18nManager.localize("confessions.modal.content_placeholder", locale))))
                    .addComponent(Label.of(
                            i18nManager.localize("confessions.modal.attachment", locale),
                            FileUpload.of("confession_upload").required(false)))
                    .build());
        });
    }

    private Mono<Void> openReplyModal(ButtonInteractionEvent event, String customId) {
        String confessionMessageId = customId.substring(REPLY_BUTTON_PREFIX.length());
        if (confessionMessageId.isBlank()) return Mono.empty();

        String guildId =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");
        return guildSettingsProvider
                .get(guildId)
                .map(settings ->
                        SupportedLocale.forLanguageTag(settings.getLocale()).getLocale())
                .flatMap(locale -> event.presentModal(InteractionPresentModalSpec.builder()
                        .customId(ConfessionModalListener.REPLY_MODAL_PREFIX + confessionMessageId)
                        .title(i18nManager.localize("confessions.reply.modal.title", locale))
                        .addComponent(Label.of(
                                i18nManager.localize("confessions.reply.modal.content", locale),
                                TextInput.paragraph(
                                                ConfessionModalListener.REPLY_INPUT_ID,
                                                1,
                                                DiscordConstants.MAX_CHARACTERS_PER_MODAL_PARAGRAPH_INPUT)
                                        .placeholder(
                                                i18nManager.localize("confessions.reply.modal.placeholder", locale))))
                        .build()));
    }
}
