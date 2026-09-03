package dev.asyncluna.zenith.discord.feature.settings;

import dev.asyncluna.zenith.core.model.GuildSettings;
import dev.asyncluna.zenith.core.repository.GuildSettingsRepository;
import dev.asyncluna.zenith.discord.command.BotCommand;
import dev.asyncluna.zenith.discord.command.Command;
import dev.asyncluna.zenith.discord.command.CommandContext;
import dev.asyncluna.zenith.discord.command.CommandException;
import dev.asyncluna.zenith.discord.command.CommandOption;
import dev.asyncluna.zenith.discord.command.SubCommand;
import discord4j.common.util.Snowflake;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(
        name = "settings",
        description = "Configure Zenith for this server.",
        defaultMemberPermissions = "8",
        ephemeral = true)
@CommandOption(
        name = "view",
        description = "View the current Zenith settings.",
        type = ApplicationCommandOption.Type.SUB_COMMAND)
@CommandOption(
        name = "confessions",
        description = "Enable or disable confession submissions.",
        type = ApplicationCommandOption.Type.SUB_COMMAND,
        subCommands = {
            @SubCommand(
                    name = "enabled",
                    description = "Whether members can submit confessions.",
                    type = ApplicationCommandOption.Type.BOOLEAN),
            @SubCommand(
                    name = "channel",
                    description = "Channel where confessions are published.",
                    type = ApplicationCommandOption.Type.CHANNEL),
            @SubCommand(
                    name = "log_channel",
                    description = "Channel where confession authors are logged.",
                    type = ApplicationCommandOption.Type.CHANNEL)
        })
@CommandOption(
        name = "member_of_the_week",
        description = "Pause or resume Member of the Week voting.",
        type = ApplicationCommandOption.Type.SUB_COMMAND,
        subCommands = {
            @SubCommand(
                    name = "paused",
                    description = "Whether automatic voting should be paused.",
                    type = ApplicationCommandOption.Type.BOOLEAN),
            @SubCommand(
                    name = "channel",
                    description = "Channel where Member of the Week voting is published.",
                    type = ApplicationCommandOption.Type.CHANNEL),
            @SubCommand(
                    name = "log_channel",
                    description = "Channel where Member of the Week votes are logged.",
                    type = ApplicationCommandOption.Type.CHANNEL)
        })
@CommandOption(
        name = "moderation",
        description = "Configure the moderation log channel.",
        type = ApplicationCommandOption.Type.SUB_COMMAND,
        subCommands = {
            @SubCommand(
                    name = "log_channel",
                    description = "Channel for moderation reminders.",
                    type = ApplicationCommandOption.Type.CHANNEL,
                    required = true)
        })
public class SettingsCommand implements BotCommand {
    private static final Color SETTINGS_COLOR = Color.of(0x5865F2);

    private final GuildSettingsRepository guildSettingsRepository;

    @Override
    public Mono<?> handle(CommandContext ctx) {
        String guildId = ctx.getEvent()
                .getInteraction()
                .getGuildId()
                .map(Snowflake::asString)
                .orElse("");
        if (guildId.isBlank()) {
            return Mono.error(new CommandException(ctx.localize("settings.error.guild_only")));
        }

        if (hasSubcommand(ctx, "view")) return view(ctx, guildId);
        if (hasSubcommand(ctx, "confessions")) return updateConfessions(ctx, guildId);
        if (hasSubcommand(ctx, "member_of_the_week")) return updateMemberOfTheWeek(ctx, guildId);
        if (hasSubcommand(ctx, "moderation")) return updateModeration(ctx, guildId);

        return Mono.error(new CommandException(ctx.localize("settings.error.action")));
    }

    private Mono<?> view(CommandContext ctx, String guildId) {
        return getSettings(guildId).flatMap(settings -> ctx.editReply()
                .withEmbeds(EmbedCreateSpec.builder()
                        .color(SETTINGS_COLOR)
                        .title(ctx.localize("settings.view.title"))
                        .addField(
                                ctx.localize("settings.view.confessions"),
                                ctx.localize(
                                        settings.isConfessionsEnabled() ? "settings.enabled" : "settings.disabled"),
                                true)
                        .addField(
                                ctx.localize("settings.view.member_of_the_week"),
                                ctx.localize(
                                        settings.isMemberOfTheWeekPaused() ? "settings.paused" : "settings.active"),
                                true)
                        .addField(
                                ctx.localize("settings.view.confessions_channel"),
                                formatChannel(settings.getConfessionsChannelId()),
                                true)
                        .addField(
                                ctx.localize("settings.view.confessions_log_channel"),
                                formatChannel(settings.getConfessionsLogChannelId()),
                                true)
                        .addField(
                                ctx.localize("settings.view.member_of_the_week_channel"),
                                formatChannel(settings.getMemberOfTheWeekChannelId()),
                                true)
                        .addField(
                                ctx.localize("settings.view.member_of_the_week_log_channel"),
                                formatChannel(settings.getMemberOfTheWeekLogChannelId()),
                                true)
                        .addField(
                                ctx.localize("settings.view.moderation_log_channel"),
                                formatChannel(settings.getModerationLogChannelId()),
                                true)
                        .build()));
    }

    private Mono<?> updateConfessions(CommandContext ctx, String guildId) {
        boolean hasEnabled = ctx.getOption("enabled").isPresent();
        boolean enabled = ctx.getOptionAsBoolean("enabled").orElse(false);
        String channelId = getChannelId(ctx, "channel");
        String logChannelId = getChannelId(ctx, "log_channel");

        return update(guildId, settings -> {
                    if (hasEnabled) settings.setConfessionsEnabled(enabled);
                    if (channelId != null) settings.setConfessionsChannelId(channelId);
                    if (logChannelId != null) settings.setConfessionsLogChannelId(logChannelId);
                })
                .then(ctx.editReply(
                        channelId != null || logChannelId != null
                                ? ctx.localize("settings.channels.updated")
                                : ctx.localize(
                                        enabled ? "settings.confessions.enabled" : "settings.confessions.disabled")));
    }

    private Mono<?> updateMemberOfTheWeek(CommandContext ctx, String guildId) {
        boolean hasPaused = ctx.getOption("paused").isPresent();
        boolean paused = ctx.getOptionAsBoolean("paused").orElse(false);
        String channelId = getChannelId(ctx, "channel");
        String logChannelId = getChannelId(ctx, "log_channel");

        return update(guildId, settings -> {
                    if (hasPaused) settings.setMemberOfTheWeekPaused(paused);
                    if (channelId != null) settings.setMemberOfTheWeekChannelId(channelId);
                    if (logChannelId != null) settings.setMemberOfTheWeekLogChannelId(logChannelId);
                })
                .then(ctx.editReply(
                        channelId != null || logChannelId != null
                                ? ctx.localize("settings.channels.updated")
                                : ctx.localize(
                                        paused
                                                ? "settings.member_of_the_week.paused"
                                                : "settings.member_of_the_week.resumed")));
    }

    private Mono<?> updateModeration(CommandContext ctx, String guildId) {
        String channelId = getChannelId(ctx, "log_channel");
        return update(guildId, settings -> settings.setModerationLogChannelId(channelId))
                .then(ctx.editReply(ctx.localize("settings.channels.updated")));
    }

    private Mono<Void> update(String guildId, Consumer<GuildSettings> change) {
        return getSettings(guildId)
                .map(settings -> {
                    settings.setId(guildId);
                    change.accept(settings);
                    return settings;
                })
                .flatMap(guildSettingsRepository::save)
                .then();
    }

    private Mono<GuildSettings> getSettings(String guildId) {
        return guildSettingsRepository
                .findById(guildId)
                .defaultIfEmpty(GuildSettings.builder().id(guildId).build());
    }

    private boolean hasSubcommand(CommandContext ctx, String name) {
        return ctx.getEvent().getOption(name).isPresent();
    }

    private String getChannelId(CommandContext ctx, String optionName) {
        return ctx.getOptionAsSnowflake(optionName).map(Snowflake::asString).orElse(null);
    }

    private String formatChannel(String channelId) {
        return channelId == null || channelId.isBlank() ? "-" : "<#" + channelId + ">";
    }
}
