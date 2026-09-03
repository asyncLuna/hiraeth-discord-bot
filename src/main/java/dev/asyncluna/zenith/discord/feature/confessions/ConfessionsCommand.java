package dev.asyncluna.zenith.discord.feature.confessions;

import dev.asyncluna.zenith.core.model.GuildSettings;
import dev.asyncluna.zenith.core.repository.GuildSettingsRepository;
import dev.asyncluna.zenith.discord.command.BotCommand;
import dev.asyncluna.zenith.discord.command.Command;
import dev.asyncluna.zenith.discord.command.CommandContext;
import dev.asyncluna.zenith.discord.command.CommandOption;
import discord4j.common.util.Snowflake;
import discord4j.core.object.command.ApplicationCommandOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(
        name = "confessions",
        description = "Enable or disable confession submissions.",
        defaultMemberPermissions = "16",
        ephemeral = true)
@CommandOption(
        name = "enabled",
        description = "Whether members can submit confessions.",
        type = ApplicationCommandOption.Type.BOOLEAN,
        required = true)
public class ConfessionsCommand implements BotCommand {
    private final GuildSettingsRepository guildSettingsRepository;

    @Override
    public Mono<?> handle(CommandContext ctx) {
        String guildId = ctx.getEvent()
                .getInteraction()
                .getGuildId()
                .map(Snowflake::asString)
                .orElse("");
        boolean enabled = ctx.getOptionAsBoolean("enabled").orElse(true);

        return guildSettingsRepository
                .findById(guildId)
                .defaultIfEmpty(GuildSettings.builder().id(guildId).build())
                .flatMap(settings -> {
                    settings.setConfessionsEnabled(enabled);
                    return guildSettingsRepository.save(settings);
                })
                .then(ctx.editReply(ctx.localize(enabled ? "confessions.enabled" : "confessions.disabled")))
                .onErrorResume(__ -> ctx.editReply(ctx.localize("confessions.update_failed")));
    }
}
