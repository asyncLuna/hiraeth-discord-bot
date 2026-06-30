package dev.asyncluna.hiraeth.discord.listener.listeners;

import dev.asyncluna.hiraeth.discord.listener.EventListener;
import dev.asyncluna.hiraeth.discord.util.DiscordConstants;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.FileUpload;
import discord4j.core.object.component.Label;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.InteractionPresentModalSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfessionButtonListener implements EventListener<ButtonInteractionEvent> {
  @Override
  public Mono<Void> execute(ButtonInteractionEvent event) {
    if (!event.getCustomId().equals("submit_a_confession_button")) return Mono.empty();

    log.info(
        "Presenting a modal | Modal: {} | Guild: {} | User: {} ({})",
        "confession_modal",
        event.getInteraction().getGuildId().orElse(null),
        event.getInteraction().getUser().getUsername(),
        event.getInteraction().getUser().getId().asString());

    return event.presentModal(
        InteractionPresentModalSpec.builder()
            .customId("confession_modal")
            .title("Submit a Confession")
            .addComponent(
                Label.of(
                    "Content",
                    TextInput.paragraph(
                            "confession_input",
                            1,
                            DiscordConstants.MAX_CHARACTERS_PER_MODAL_PARAGRAPH_INPUT)
                        .placeholder("I'm actually a cat. Meow meow.")))
            .addComponent(
                Label.of(
                    "Attachment (optional)", FileUpload.of("confession_upload").required(false)))
            .build());
  }
}
