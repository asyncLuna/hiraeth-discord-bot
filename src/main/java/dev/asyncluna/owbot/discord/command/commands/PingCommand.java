package dev.asyncluna.owbot.discord.command.commands;

import dev.asyncluna.owbot.discord.command.BotCommand;
import dev.asyncluna.owbot.discord.command.Command;
import dev.asyncluna.owbot.discord.command.CommandContext;
import java.time.Duration;
import java.time.Instant;

import discord4j.gateway.GatewayClient;
import reactor.core.publisher.Mono;

@Command(name = "ping", description = "Checks the bot's latency.")
public class PingCommand implements BotCommand {
  @Override
  public Mono<Void> handle(CommandContext ctx) {
    int shardIndex = ctx.getEvent().getShardInfo().getIndex();

    long latencyMs =
        ctx.getEvent()
            .getClient()
            .getGatewayClient(shardIndex)
            .map(GatewayClient::getResponseTime)
            .map(Duration::toMillis)
            .orElse(0L);

    String localizedResponse = ctx.localize("commands.ping.response", latencyMs);

    return ctx.editReply(localizedResponse).then();
  }
}
