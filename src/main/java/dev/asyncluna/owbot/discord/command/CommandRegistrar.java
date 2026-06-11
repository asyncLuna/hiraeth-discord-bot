package dev.asyncluna.owbot.discord.command;

import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.json.ApplicationCommandRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommandRegistrar implements SmartInitializingSingleton {
  private final GatewayDiscordClient gateway;
  private final CommandDispatcher commandDispatcher;

  @Override
  public void afterSingletonsInstantiated() {
    registerGlobalSlashCommands()
        .doOnSubscribe(
            __ -> log.info("Starting global Discord application command synchronization"))
        .subscribe();
  }

  private Mono<Void> registerGlobalSlashCommands() {
    List<ApplicationCommandRequest> requests = commandDispatcher.getCommandRequests();

    log.info("Synchronizing {} command(s) globally", requests.size());

    return gateway
        .getRestClient()
        .getApplicationId()
        .flatMap(
            appId ->
                gateway
                    .getRestClient()
                    .getApplicationService()
                    .bulkOverwriteGlobalApplicationCommand(appId, requests)
                    .collectList()
                    .doOnNext(
                        data ->
                            log.info(
                                "Successfully synchronized {} global application command(s)",
                                data.size()))
                    .onErrorResume(
                        exception -> {
                          log.error(
                              "Failed to register global application slash commands", exception);
                          return Mono.empty();
                        }))
        .then();
  }
}
