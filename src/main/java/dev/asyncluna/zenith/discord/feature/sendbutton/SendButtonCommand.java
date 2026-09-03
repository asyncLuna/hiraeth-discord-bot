package dev.asyncluna.zenith.discord.feature.sendbutton;

import dev.asyncluna.zenith.discord.command.BotCommand;
import dev.asyncluna.zenith.discord.command.Command;
import dev.asyncluna.zenith.discord.command.CommandContext;
import dev.asyncluna.zenith.discord.command.CommandException;
import dev.asyncluna.zenith.discord.command.CommandOption;
import dev.asyncluna.zenith.discord.util.DiscordConstants;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.emoji.Emoji;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.rest.util.Color;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(
        name = "send_button",
        description = "Send a predefined button from a list.",
        defaultMemberPermissions = "16", // MANAGE_CHANNELS
        ephemeral = true)
@CommandOption(
        name = "type",
        description = "The button to send.",
        type = ApplicationCommandOption.Type.STRING,
        autocomplete = true,
        required = true)
@CommandOption(
        name = "channel",
        description = "The channel to send the button to. Defaults to current.",
        type = ApplicationCommandOption.Type.CHANNEL)
public class SendButtonCommand implements BotCommand {
    private static final String SUBMIT_A_CONFESSION_BUTTON_ID = "submit_a_confession_button";

    private static final Map<String, String> AVAILABLE_BUTTONS = Map.of(SUBMIT_A_CONFESSION_BUTTON_ID, "Confession");

    @Override
    public Mono<?> handle(CommandContext ctx) {
        Optional<String> buttonIdOption = ctx.getOptionAsString("type");

        if (buttonIdOption.isEmpty() || !AVAILABLE_BUTTONS.containsKey(buttonIdOption.get()))
            return Mono.error(new CommandException(ctx.localize("send_button.error.invalid_id")));

        String buttonId = buttonIdOption.get();

        Mono<TextChannel> targetChannelMono = Mono.justOrEmpty(ctx.getOptionAsChannel("channel"))
                .cast(Channel.class)
                .switchIfEmpty(ctx.getEvent().getInteraction().getChannel().cast(Channel.class))
                .flatMap(channel -> {
                    if (channel.getType() != Channel.Type.GUILD_TEXT)
                        return Mono.error(new CommandException(ctx.localize("send_button.error.text_channel_only")));

                    return Mono.just((TextChannel) channel);
                });

        return targetChannelMono
                .flatMap(textChannel -> {
                    MessageCreateSpec messageSpec;

                    if (buttonId.equals(SUBMIT_A_CONFESSION_BUTTON_ID)) {
                        EmbedCreateSpec embed = EmbedCreateSpec.builder()
                                .title(ctx.localize("send_button.confession.embed.title"))
                                .description(ctx.localize("send_button.confession.embed.description"))
                                .color(Color.CYAN)
                                .build();

                        Button button = Button.primary(
                                SUBMIT_A_CONFESSION_BUTTON_ID,
                                Emoji.unicode("\uD83E\uDD2B"),
                                ctx.localize("send_button.confession.button.label"));

                        messageSpec = MessageCreateSpec.builder()
                                .addEmbed(embed)
                                .addComponent(ActionRow.of(button))
                                .build();
                    } else {
                        return Mono.error(new CommandException(ctx.localize("send_button.error.unimplemented")));
                    }

                    return textChannel.createMessage(messageSpec);
                })
                .then(ctx.editReply(ctx.localize("send_button.success")));
    }

    @Override
    public Mono<Void> autocomplete(ChatInputAutoCompleteEvent event) {
        ApplicationCommandInteractionOption focusedOption = event.getFocusedOption();
        String optionName = focusedOption.getName();
        String userInput = focusedOption
                .getValue()
                .map(ApplicationCommandInteractionOptionValue::asString)
                .map(String::toLowerCase)
                .orElse("");

        Mono<List<ApplicationCommandOptionChoiceData>> choicesMono = optionName.equals("type")
                ? Flux.fromIterable(AVAILABLE_BUTTONS.entrySet())
                        .filter(entry -> entry.getKey().toLowerCase().contains(userInput)
                                || entry.getValue().toLowerCase().contains(userInput))
                        .take(DiscordConstants.MAX_AUTO_COMPLETE_RESULTS)
                        .map(entry -> (ApplicationCommandOptionChoiceData) ApplicationCommandOptionChoiceData.builder()
                                .name(entry.getValue())
                                .value(entry.getKey())
                                .build())
                        .collectList()
                : Mono.just(Collections.emptyList());

        return choicesMono.flatMap(event::respondWithSuggestions).then();
    }
}
