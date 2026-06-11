package dev.asyncluna.owbot.discord.command;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface BotCommand {
  Mono<Void> handle(CommandContext ctx);
}
