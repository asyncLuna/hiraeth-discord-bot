package dev.asyncluna.zenith.discord.feature.purge;

import dev.asyncluna.zenith.discord.command.BotCommand;
import dev.asyncluna.zenith.discord.command.Command;
import dev.asyncluna.zenith.discord.command.CommandContext;
import dev.asyncluna.zenith.discord.command.CommandException;
import dev.asyncluna.zenith.discord.command.CommandOption;
import dev.asyncluna.zenith.discord.util.DiscordUtils;
import dev.asyncluna.zenith.discord.util.NumberUtils;
import discord4j.common.util.Snowflake;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.rest.util.Permission;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(
        name = "purge_messages",
        description = "Delete messages from a member.",
        defaultMemberPermissions = "8192") // MANAGE_MESSAGES
@CommandOption(
        name = "author",
        description = "The user whose messages will be deleted.",
        type = ApplicationCommandOption.Type.USER,
        required = true)
@CommandOption(
        name = "limit",
        description = "The number of messages to delete (1-1000). Defaults to 1.",
        type = ApplicationCommandOption.Type.INTEGER)
public class PurgeMessagesCommand implements BotCommand {
    private static final long MIN_MESSAGES = 1;
    private static final long MAX_MESSAGES = 1000;
    private static final long MESSAGES_OFFSET = 1;

    @Override
    public Mono<?> handle(CommandContext ctx) {
        return ctx.editReply(":hourglass: " + ctx.localize("prune.loading"))
                .then(getLimit(ctx))
                .flatMap(limit -> ctx.getEvent()
                        .getInteraction()
                        .getGuild()
                        .flatMapMany(Guild::getChannels)
                        .ofType(GuildMessageChannel.class)
                        .flatMap(channel -> DiscordUtils.requirePermissions(
                                        channel, Permission.MANAGE_MESSAGES, Permission.READ_MESSAGE_HISTORY)
                                .thenMany(Flux.defer(() -> {
                                    Optional<Mono<User>> authorMonoOpt = ctx.getOptionAsUser("author");
                                    Mono<Optional<Snowflake>> authorIdOptMono = authorMonoOpt
                                            .map(userMono -> userMono.map(User::getId)
                                                    .map(Optional::of)
                                                    .defaultIfEmpty(Optional.empty()))
                                            .orElseGet(() -> Mono.just(Optional.empty()));

                                    return authorIdOptMono.flatMapMany(
                                            authorOpt -> channel.getMessagesBefore(Snowflake.of(Long.MAX_VALUE))
                                                    .filter(filterMessage(authorOpt.orElse(null))));
                                }))
                                .onErrorResume(exception -> Flux.empty()))
                        .take(limit)
                        .groupBy(Message::getChannelId)
                        .flatMap(groupedFlux -> {
                            Snowflake channelId = groupedFlux.key();
                            return groupedFlux.map(Message::getId).collectList().flatMap(messageIds -> {
                                if (messageIds.isEmpty()) return Mono.just(0L);
                                return ctx.getEvent()
                                        .getClient()
                                        .getChannelById(channelId)
                                        .cast(GuildMessageChannel.class)
                                        .flatMap(channel -> channel.bulkDelete(Flux.fromIterable(messageIds))
                                                .count()
                                                .map(messagesNotDeleted ->
                                                        Math.max(0L, messageIds.size() - messagesNotDeleted)));
                            });
                        })
                        .reduce(0L, Long::sum))
                .flatMap(totalMessagesDeleted -> ctx.editReply(":white_check_mark: "
                        + ctx.localize("prune.messages.deleted").formatted(totalMessagesDeleted)));
    }

    private Mono<Long> getLimit(CommandContext ctx) {
        Optional<Long> limitOpt = ctx.getOptionAsLong("limit");
        long limit = limitOpt.orElse(MIN_MESSAGES);

        if (!NumberUtils.isBetween(limit, MIN_MESSAGES, MAX_MESSAGES))
            return Mono.error(new CommandException(
                    ctx.localize("prune.limit_out_of_range").formatted(MIN_MESSAGES, MAX_MESSAGES)));

        return Mono.just(Math.min(MAX_MESSAGES, limit));
    }

    private Predicate<Message> filterMessage(Snowflake authorId) {
        return message -> (authorId == null
                || message.getAuthor().map(User::getId).map(authorId::equals).orElse(false));
    }
}
