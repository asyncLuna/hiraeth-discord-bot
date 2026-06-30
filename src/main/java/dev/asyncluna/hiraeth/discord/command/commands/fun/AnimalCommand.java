package dev.asyncluna.hiraeth.discord.command.commands.fun;

import dev.asyncluna.hiraeth.core.integration.animalapi.AnimalApiService;
import dev.asyncluna.hiraeth.core.integration.animalapi.AnimalApiType;
import dev.asyncluna.hiraeth.discord.command.BotCommand;
import dev.asyncluna.hiraeth.discord.command.Command;
import dev.asyncluna.hiraeth.discord.command.CommandContext;
import dev.asyncluna.hiraeth.discord.command.CommandException;
import dev.asyncluna.hiraeth.discord.command.CommandOption;
import dev.asyncluna.hiraeth.discord.util.DiscordConstants;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Color;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(name = "animal", description = "Send a random animal image and fact.")
@CommandOption(
    name = "type",
    description = "Animal type, e.g. cat, dog, capybara",
    type = ApplicationCommandOption.Type.STRING,
    autocomplete = true,
    required = true)
public class AnimalCommand implements BotCommand {
  private final AnimalApiService animalApiService;

  @Override
  public Mono<?> handle(CommandContext ctx) {
    Optional<String> animalOption = ctx.getOptionAsString("type").map(String::toLowerCase);

    if (animalOption.isEmpty())
      return Mono.error(new CommandException(ctx.localize("animal.error.missing_type")));

    AnimalApiType animalApiType;
    try {
      animalApiType = AnimalApiType.fromApiName(animalOption.get());
    } catch (IllegalArgumentException exception) {
      return Mono.error(new CommandException(ctx.localize("animal.error.invalid_type")));
    }

    return animalApiService
        .getRandomAnimalImageAndFact(animalApiType)
        .flatMap(
            response -> {
              EmbedCreateSpec embed =
                  EmbedCreateSpec.builder()
                      .title(animalApiType.getFriendlyName())
                      .image(response.image())
                      .description(response.fact())
                      .color(Color.of(0xFF9900))
                      .build();

              return ctx.editReply().withEmbeds(embed);
            })
        .switchIfEmpty(Mono.error(new CommandException(ctx.localize("animal.error.no_data"))))
        .onErrorResume(
            exception -> {
              if (exception instanceof CommandException) return Mono.error(exception);
              return Mono.error(new CommandException(ctx.localize("animal.error.failed_fetch")));
            });
  }

  @Override
  public Mono<Void> autocomplete(ChatInputAutoCompleteEvent event) {
    ApplicationCommandInteractionOption focusedOption = event.getFocusedOption();
    String optionName = focusedOption.getName();
    String userInput =
        focusedOption
            .getValue()
            .map(ApplicationCommandInteractionOptionValue::asString)
            .map(String::toLowerCase)
            .orElse("");

    Mono<List<ApplicationCommandOptionChoiceData>> choicesMono =
        optionName.equals("type")
            ? Flux.fromArray(AnimalApiType.values())
                .filter(
                    type ->
                        type.getApiName().toLowerCase().contains(userInput)
                            || type.getFriendlyName().toLowerCase().contains(userInput))
                .take(DiscordConstants.MAX_AUTO_COMPLETE_RESULTS)
                .map(
                    type ->
                        (ApplicationCommandOptionChoiceData)
                            ApplicationCommandOptionChoiceData.builder()
                                .name(type.getFriendlyName())
                                .value(type.getApiName())
                                .build())
                .collectList()
            : Mono.just(Collections.emptyList());

    return choicesMono.flatMap(event::respondWithSuggestions).then();
  }
}
