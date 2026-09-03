package dev.asyncluna.zenith.discord.command;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.model.GuildSettings;
import dev.asyncluna.zenith.discord.DeferrableInteractionContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.MessageChannel;
import java.util.Optional;
import reactor.core.publisher.Mono;

public class CommandContext extends DeferrableInteractionContext<ChatInputInteractionEvent> {
    public CommandContext(ChatInputInteractionEvent event, GuildSettings guildSettings, I18nManager i18nManager) {
        super(event, guildSettings, i18nManager);
    }

    public Optional<ApplicationCommandInteractionOptionValue> getOption(String name) {
        Optional<ApplicationCommandInteractionOption> topOption = getEvent().getOption(name);
        if (topOption.isPresent()) return topOption.flatMap(ApplicationCommandInteractionOption::getValue);

        return getEvent().getOptions().stream()
                .flatMap(subOption -> subOption.getOption(name).stream())
                .findFirst()
                .flatMap(ApplicationCommandInteractionOption::getValue);
    }

    public Optional<String> getOptionAsString(String name) {
        return getOption(name).map(ApplicationCommandInteractionOptionValue::asString);
    }

    public Optional<Boolean> getOptionAsBoolean(String name) {
        return getOption(name).map(ApplicationCommandInteractionOptionValue::asBoolean);
    }

    public Optional<Long> getOptionAsLong(String name) {
        return getOption(name).map(ApplicationCommandInteractionOptionValue::asLong);
    }

    public Optional<Double> getOptionAsDouble(String name) {
        return getOption(name).map(ApplicationCommandInteractionOptionValue::asDouble);
    }

    public Optional<Snowflake> getOptionAsSnowflake(String name) {
        return getOption(name).map(ApplicationCommandInteractionOptionValue::asSnowflake);
    }

    public Optional<Mono<User>> getOptionAsUser(String name) {
        return getOption(name).map(ApplicationCommandInteractionOptionValue::asUser);
    }

    public Optional<Mono<Channel>> getOptionAsChannel(String name) {
        return getOption(name).map(ApplicationCommandInteractionOptionValue::asChannel);
    }

    public Optional<Attachment> getOptionAsAttachment(String name) {
        return getOption(name).map(ApplicationCommandInteractionOptionValue::asAttachment);
    }

    public Mono<MessageChannel> getChannel() {
        return getEvent().getInteraction().getChannel();
    }
}
