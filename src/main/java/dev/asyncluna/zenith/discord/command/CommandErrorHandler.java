package dev.asyncluna.zenith.discord.command;

import dev.asyncluna.zenith.discord.util.EmbedUtils;
import discord4j.core.spec.EmbedCreateSpec;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CommandErrorHandler {
  public Mono<Void> handle(CommandContext context, String commandName, Throwable exception) {
    String message;

    if (exception instanceof CommandException) {
      message = exception.getMessage();
    } else if (exception instanceof WebClientResponseException webException) {
      String rawJson = webException.getResponseBodyAsString();
      log.info("API error body payload for '/{}': {}", commandName, rawJson);
      message = parseApiErrorMessage(rawJson);
      if (message == null) message = context.localize("error.command_execution_failed_description");
    } else {
      log.error("Unhandled exception executing command '/{}'", commandName, exception);
      message = context.localize("error.command_execution_failed_description");
    }

    return context
        .editReply()
        .withEmbeds(
            EmbedCreateSpec.builder()
                .title(context.localize("error.command_execution_failed_title"))
                .description(message)
                .color(EmbedUtils.ERROR_COLOR)
                .build())
        .then();
  }

  private String parseApiErrorMessage(String json) {
    if (json == null || !json.contains("\"error\"")) return null;
    Matcher matcher = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
    return matcher.find() ? matcher.group(1) : null;
  }
}
