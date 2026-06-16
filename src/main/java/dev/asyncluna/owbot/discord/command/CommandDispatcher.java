package dev.asyncluna.owbot.discord.command;

import dev.asyncluna.owbot.core.i18n.I18nManager;
import dev.asyncluna.owbot.core.i18n.SupportedLocale;
import dev.asyncluna.owbot.core.model.GuildSettings;
import dev.asyncluna.owbot.core.repository.GuildSettingsRepository;
import dev.asyncluna.owbot.discord.util.EmbedUtils;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CommandDispatcher {
  private final Map<String, BotCommand> commandMap = new HashMap<>();
  @Getter private final List<ApplicationCommandRequest> commandRequests = new ArrayList<>();
  private final GuildSettingsRepository guildSettingsRepository;
  private final I18nManager i18nManager;

  public CommandDispatcher(
      List<BotCommand> commands,
      GuildSettingsRepository guildSettingsRepository,
      I18nManager i18nManager) {
    this.guildSettingsRepository = guildSettingsRepository;
    this.i18nManager = i18nManager;

    List<String> registeredNames = new ArrayList<>();

    for (BotCommand command : commands) {
      Command meta = command.getClass().getAnnotation(Command.class);
      if (meta == null) {
        log.warn(
            "Command bean {} is missing the @Command annotation! Skipping.",
            command.getClass().getSimpleName());
        continue;
      }
      commandMap.put(meta.name(), command);
      registeredNames.add(meta.name());

      List<ApplicationCommandOptionData> optionDataList =
          Arrays.stream(command.getClass().getAnnotationsByType(CommandOption.class))
              .map(this::mapOption)
              .toList();

      ApplicationCommandRequest request =
          ApplicationCommandRequest.builder()
              .name(meta.name())
              .description(meta.description())
              .options(optionDataList)
              .defaultMemberPermissions(meta.defaultMemberPermissions())
              .dmPermission(meta.dmPermission())
              .build();

      commandRequests.add(request);
    }

    log.info(
        "Successfully cached {} local application commands: {}",
        commandRequests.size(),
        String.join(", ", registeredNames));
  }

  public Mono<Void> dispatch(ChatInputInteractionEvent event) {
    return Mono.defer(
        () -> {
          String commandName = event.getCommandName();
          BotCommand command = commandMap.get(commandName);

          boolean isEphemeral = false;
          if (command != null) {
            Command meta = command.getClass().getAnnotation(Command.class);
            if (meta != null) isEphemeral = meta.ephemeral();
          }

          return event
              .deferReply()
              .withEphemeral(isEphemeral)
              .then(
                  Mono.defer(
                      () -> {
                        String guildIdStr =
                            event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");

                        return guildSettingsRepository
                            .findById(guildIdStr)
                            .onErrorResume(
                                exception -> {
                                  log.error(
                                      "Database is unavailable while dispatching '/{}', falling back to default settings",
                                      commandName,
                                      exception);
                                  return Mono.just(new GuildSettings());
                                })
                            .defaultIfEmpty(new GuildSettings())
                            .flatMap(
                                settings -> {
                                  if (command == null) {
                                    Locale locale =
                                        SupportedLocale.forLanguageTag(settings.getLocale())
                                            .getLocale();
                                    String localizedError =
                                        i18nManager.localize("error.unknown_command", locale);

                                    return event.editReply(localizedError).then();
                                  }

                                  logCommandExecution(event, commandName);

                                  CommandContext ctx =
                                      new CommandContext(event, settings, i18nManager);

                                  return command
                                      .handle(ctx)
                                      .onErrorResume(
                                          commandException -> {
                                            String userErrorMessage =
                                                ctx.localize(
                                                    "error.command_execution_failed_description");

                                            if (commandException
                                                instanceof WebClientResponseException exception) {
                                              String rawJson = exception.getResponseBodyAsString();
                                              log.debug(
                                                  "API error body payload for '/{}': {}",
                                                  commandName,
                                                  rawJson);

                                              String extractedError = parseApiErrorMessage(rawJson);
                                              if (extractedError != null) {
                                                userErrorMessage = extractedError;
                                              }
                                            } else {
                                              log.error(
                                                  "Unhandled exception executing command '/{}'",
                                                  commandName,
                                                  commandException);
                                            }

                                            return ctx.editReply()
                                                .withEmbeds(
                                                    EmbedCreateSpec.builder()
                                                        .title(
                                                            ctx.localize(
                                                                "error.command_execution_failed_title"))
                                                        .description(userErrorMessage)
                                                        .color(EmbedUtils.ERROR_COLOR)
                                                        .build())
                                                .then();
                                          });
                                });
                      }));
        });
  }

  public Mono<Void> dispatchAutocomplete(ChatInputAutoCompleteEvent event) {
    return Mono.defer(
        () -> {
          String commandName = event.getCommandName();
          BotCommand command = commandMap.get(commandName);

          if (command == null) return event.respondWithSuggestions(Collections.emptyList());

          return command
              .autocomplete(event)
              .onErrorResume(
                  exception -> {
                    log.error(
                        "Unhandled exception during autocomplete execution for command '/{}'",
                        commandName,
                        exception);
                    return event.respondWithSuggestions(Collections.emptyList());
                  });
        });
  }

  private ApplicationCommandOptionData mapOption(CommandOption option) {
    return ApplicationCommandOptionData.builder()
        .name(option.name())
        .description(option.description())
        .type(option.type().getValue())
        .required(option.required())
        .autocomplete(option.autocomplete())
        .build();
  }

  private void logCommandExecution(ChatInputInteractionEvent event, String commandName) {
    String optionsLog =
        event.getOptions().stream()
            .map(
                option ->
                    option.getName()
                        + "="
                        + option
                            .getValue()
                            .map(ApplicationCommandInteractionOptionValue::asString)
                            .orElse("no-value"))
            .collect(Collectors.joining(", "));

    String formattedOptions = optionsLog.isEmpty() ? "" : " " + optionsLog;

    log.info(
        "Dispatching command: '/{}{}' | User: {} ({}) | Guild: {}",
        commandName,
        formattedOptions,
        event.getInteraction().getUser().getUsername(),
        event.getInteraction().getUser().getId().asString(),
        event.getInteraction().getGuildId().map(Snowflake::asString).orElse("DM"));
  }

  private String parseApiErrorMessage(String json) {
    if (json == null || !json.contains("\"error\"")) return null;
    try {
      Pattern pattern = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]+)\"");
      Matcher matcher = pattern.matcher(json);
      if (matcher.find()) return matcher.group(1);
    } catch (Exception exception) {
      log.warn("Failed to parse API error JSON payload", exception);
    }
    return null;
  }
}
