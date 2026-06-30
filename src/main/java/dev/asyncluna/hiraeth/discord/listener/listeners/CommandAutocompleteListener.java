package dev.asyncluna.hiraeth.discord.listener.listeners;

import dev.asyncluna.hiraeth.discord.command.CommandDispatcher;
import dev.asyncluna.hiraeth.discord.listener.EventListener;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CommandAutocompleteListener implements EventListener<ChatInputAutoCompleteEvent> {
  private final CommandDispatcher dispatcher;

  @Override
  public Mono<Void> execute(ChatInputAutoCompleteEvent event) {
    return dispatcher.dispatchAutocomplete(event);
  }
}
