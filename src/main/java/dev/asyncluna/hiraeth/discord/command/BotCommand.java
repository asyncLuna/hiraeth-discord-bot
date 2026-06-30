package dev.asyncluna.hiraeth.discord.command;

import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import java.util.Collections;
import reactor.core.publisher.Mono;

public interface BotCommand {
  Mono<Void> handle(CommandContext ctx);

  default Mono<Void> autocomplete(ChatInputAutoCompleteEvent event) {
    return event.respondWithSuggestions(Collections.emptyList());
  }
}
