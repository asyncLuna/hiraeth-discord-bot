package dev.asyncluna.zenith.discord.command;

import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CommandRegistry {
  private final Map<String, BotCommand> commands = new HashMap<>();

  @Getter private final List<ApplicationCommandRequest> commandRequests = new ArrayList<>();

  public CommandRegistry(List<BotCommand> commandBeans) {
    List<String> registeredNames = new ArrayList<>();

    for (BotCommand command : commandBeans) {
      Command meta = command.getClass().getAnnotation(Command.class);
      if (meta == null) {
        log.warn(
            "Command bean {} is missing the @Command annotation! Skipping.",
            command.getClass().getSimpleName());
        continue;
      }

      commands.put(meta.name(), command);
      registeredNames.add(meta.name());
      List<ApplicationCommandOptionData> options =
          Arrays.stream(command.getClass().getAnnotationsByType(CommandOption.class))
              .map(this::mapOption)
              .toList();

      ImmutableApplicationCommandRequest.Builder request =
          ApplicationCommandRequest.builder()
              .name(meta.name())
              .description(meta.description())
              .options(options)
              .dmPermission(meta.dmPermission());
      if (meta.defaultMemberPermissions() != null && !meta.defaultMemberPermissions().isBlank())
        request.defaultMemberPermissions(meta.defaultMemberPermissions());
      commandRequests.add(request.build());
    }

    log.info(
        "Successfully cached {} local application commands: {}",
        commandRequests.size(),
        String.join(", ", registeredNames));
  }

  public BotCommand get(String name) {
    return commands.get(name);
  }

  private ApplicationCommandOptionData mapOption(CommandOption option) {
    List<ApplicationCommandOptionData> subCommands =
        Arrays.stream(option.subCommands()).map(this::mapSubCommandOption).toList();
    return ApplicationCommandOptionData.builder()
        .name(option.name())
        .description(option.description())
        .type(option.type().getValue())
        .required(option.required())
        .autocomplete(option.autocomplete())
        .addAllOptions(subCommands)
        .build();
  }

  private ApplicationCommandOptionData mapSubCommandOption(SubCommand subCommand) {
    return ApplicationCommandOptionData.builder()
        .name(subCommand.name())
        .description(subCommand.description())
        .type(subCommand.type().getValue())
        .required(subCommand.required())
        .autocomplete(subCommand.autocomplete())
        .build();
  }
}
