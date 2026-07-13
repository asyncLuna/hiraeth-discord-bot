package dev.asyncluna.hiraeth.discord.command.commands.other;

import dev.asyncluna.hiraeth.discord.command.BotCommand;
import dev.asyncluna.hiraeth.discord.command.Command;
import dev.asyncluna.hiraeth.discord.command.CommandContext;
import discord4j.gateway.GatewayClient;
import java.time.Duration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Command(name = "ping", description = "Check the bot's latency.", ephemeral = true)
public class PingCommand implements BotCommand {
  @Override
  public Mono<?> handle(CommandContext ctx) {
    int shardIndex = ctx.getEvent().getShardInfo().getIndex();

    long latencyMs =
        ctx.getEvent()
            .getClient()
            .getGatewayClient(shardIndex)
            .map(GatewayClient::getResponseTime)
            .map(Duration::toMillis)
            .orElse(0L);

    String localizedResponse = ctx.localize("ping.response", latencyMs);

    return ctx.editReply(localizedResponse);
  }
}
