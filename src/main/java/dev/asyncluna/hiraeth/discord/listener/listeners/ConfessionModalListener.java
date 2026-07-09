package dev.asyncluna.hiraeth.discord.listener.listeners;

import dev.asyncluna.hiraeth.discord.listener.EventListener;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.FileUpload;
import discord4j.core.object.component.TextInput;
import discord4j.core.object.emoji.Emoji;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfessionModalListener implements EventListener<ModalSubmitInteractionEvent> {
  private static final Color CONFESSION_BLUE = Color.of(0x3498DB);

  @Override
  public Mono<Void> execute(ModalSubmitInteractionEvent event) {
    if (!event.getCustomId().equals("confession_modal")) return Mono.empty();

    log.info(
        "Received confession modal submission | Guild: {} | User: {} ({})",
        event.getInteraction().getGuildId().map(Snowflake::asString).orElse("N/A"),
        event.getInteraction().getUser().getUsername(),
        event.getInteraction().getUser().getId().asString());

    String confessionMessage =
        event.getComponents(TextInput.class).stream()
            .filter(component -> component.getCustomId().equals("confession_input"))
            .findFirst()
            .flatMap(TextInput::getValue)
            .orElse("");

    if (confessionMessage.isBlank())
      return event
          .reply(
              InteractionApplicationCommandCallbackSpec.builder()
                  .content("Confession content cannot be empty!")
                  .ephemeral(true)
                  .build())
          .then();

    Attachment attachment =
        event.getComponents(FileUpload.class).stream()
            .filter(component -> component.getCustomId().equals("confession_upload"))
            .findFirst()
            .flatMap(
                component ->
                    component.getValues().orElse(Collections.emptyList()).stream().findFirst())
            .flatMap(
                attachmentId ->
                    event
                        .getResolved()
                        .map(resolved -> resolved.getAttachments().get(attachmentId)))
            .orElse(null);

    User author = event.getInteraction().getUser();

    return event
        .deferReply()
        .withEphemeral(true)
        .then(
            event
                .getInteraction()
                .getGuild()
                .flatMap(
                    guild ->
                        guild
                            .getChannels()
                            .filter(channel -> channel instanceof TextChannel)
                            .cast(TextChannel.class)
                            .collectList()
                            .flatMap(
                                channels -> {
                                  TextChannel confessionsChannel =
                                      channels.stream()
                                          .filter(
                                              channel ->
                                                  channel.getName().equalsIgnoreCase("confessions"))
                                          .findFirst()
                                          .orElse(null);

                                  TextChannel logChannel =
                                      channels.stream()
                                          .filter(
                                              channel ->
                                                  channel
                                                      .getName()
                                                      .equalsIgnoreCase("confessions﹒log"))
                                          .findFirst()
                                          .orElse(null);

                                  if (confessionsChannel == null) {
                                    return Mono.error(
                                        new IllegalStateException(
                                            "Confessions channel was not found."));
                                  }

                                  EmbedCreateSpec.Builder publicEmbedBuilder =
                                      EmbedCreateSpec.builder()
                                          .title("Confession")
                                          .description(confessionMessage)
                                          .color(CONFESSION_BLUE);

                                  if (attachment != null)
                                    publicEmbedBuilder.image(attachment.getUrl());

                                  Button confessionButton =
                                      Button.primary(
                                          "submit_a_confession_button",
                                          Emoji.unicode("\uD83E\uDD2B"),
                                          "Submit a Confession");

                                  MessageCreateSpec publicMessageSpec =
                                      MessageCreateSpec.builder()
                                          .addEmbed(publicEmbedBuilder.build())
                                          .addComponent(ActionRow.of(confessionButton))
                                          .build();

                                  Mono<Void> sendPublic =
                                      confessionsChannel.createMessage(publicMessageSpec).then();

                                  Mono<Void> sendToLog = Mono.empty();
                                  if (logChannel != null) {
                                    EmbedCreateSpec.Builder logEmbedBuilder =
                                        EmbedCreateSpec.builder()
                                            .title("Confession Log")
                                            .description(confessionMessage)
                                            .addField(
                                                "Author",
                                                author.getMention()
                                                    + " ("
                                                    + author.getId().asString()
                                                    + ")",
                                                false)
                                            .color(CONFESSION_BLUE);

                                    if (attachment != null)
                                      logEmbedBuilder.image(attachment.getUrl());

                                    sendToLog =
                                        logChannel.createMessage(logEmbedBuilder.build()).then();
                                  }

                                  return Mono.when(sendPublic, sendToLog);
                                })))
        .then(event.editReply("Your confession has been successfully sent!"))
        .onErrorResume(
            exception -> {
              log.error("Failed to process confession modal", exception);
              return event.editReply("An error occurred while sending your confession.");
            })
        .then();
  }
}
