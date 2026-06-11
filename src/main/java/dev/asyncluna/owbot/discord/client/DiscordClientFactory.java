package dev.asyncluna.owbot.discord.client;

import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DiscordBotProperties.class)
public class DiscordClientFactory {
  @Bean
  public GatewayDiscordClient gatewayDiscordClient(DiscordBotProperties properties) {
    if (properties.getToken() == null || properties.getToken().isBlank()) {
      throw new IllegalStateException(
          "DISCORD_BOT_TOKEN environment variable not set or empty");
    }

    return DiscordClient.create(properties.getToken())
        .gateway()
        .setEnabledIntents(
            IntentSet.of(
                Intent.GUILDS, Intent.GUILD_MEMBERS, Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT))
        .setInitialPresence(env -> ClientPresence.online(ClientActivity.custom("Pocketing you")))
        .login()
        .block();
  }
}
