package dev.asyncluna.zenith.discord.util;

import dev.asyncluna.zenith.discord.command.MissingPermissionException;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.PrivateChannel;
import discord4j.rest.util.Permission;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class DiscordUtils {
    public static Mono<Boolean> hasPermission(Channel channel, Snowflake userId, Permission permission) {
        if (channel instanceof PrivateChannel) return Mono.just(true);
        return ((GuildChannel) channel)
                .getEffectivePermissions(userId)
                .map(permissions -> permissions.contains(permission));
    }

    public static Mono<Void> requirePermissions(Channel channel, Permission... permissions) {
        return Flux.fromArray(permissions)
                .flatMap(
                        permission -> hasPermission(channel, channel.getClient().getSelfId(), permission)
                                .filter(Boolean.TRUE::equals)
                                .switchIfEmpty(Mono.error(new MissingPermissionException(permission))))
                .then();
    }
}
