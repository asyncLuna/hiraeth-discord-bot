package dev.asyncluna.hiraeth.discord.listener.listeners;

import dev.asyncluna.hiraeth.discord.listener.EventListener;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.guild.BanEvent;
import discord4j.core.object.audit.ActionType;
import discord4j.core.object.audit.AuditLogPart;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.AuditLogQuerySpec;
import discord4j.core.spec.MessageCreateSpec;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class BanReasonListener implements EventListener<BanEvent> {
  private static final Snowflake MOD_CHANNEL_ID = Snowflake.of("1520157330946261202");
  private static final String DEFAULT_BAN_REASON = "No reason provided";

  @Override
  public Mono<Void> execute(BanEvent event) {
    log.debug(
        "BanEvent received | guild={} | user={} ({})",
        event.getGuildId().asString(),
        event.getUser().getTag(),
        event.getUser().getId().asString());

    return Mono.just(event)
        .delayElement(Duration.ofSeconds(1))
        .flatMap(
            e ->
                e.getGuild()
                    .doOnNext(
                        guild ->
                            log.debug(
                                "Fetching ban audit log | guild={} | bannedUser={}",
                                guild.getId().asString(),
                                e.getUser().getId().asString()))
                    .flatMap(
                        guild ->
                            guild
                                .getAuditLog(
                                    AuditLogQuerySpec.builder()
                                        .actionType(ActionType.MEMBER_BAN_ADD)
                                        .build())
                                .doOnNext(
                                    part ->
                                        log.debug(
                                            "Received audit log part | guild={} | entries={}",
                                            part.getGuildId().asString(),
                                            part.getEntries().size()))
                                .next()
                                .switchIfEmpty(
                                    Mono.fromRunnable(
                                        () ->
                                            log.debug(
                                                "No audit log part returned for ban event | guild={} | bannedUser={}",
                                                e.getGuildId().asString(),
                                                e.getUser().getId().asString())))
                                .flatMap(part -> processAuditPart(part, e))))
        .onErrorResume(this::handleException)
        .then();
  }

  private Mono<Void> processAuditPart(AuditLogPart part, BanEvent event) {
    try {
      String bannedId = event.getUser().getId().asString();

      log.debug(
          "Inspecting audit log part | guild={} | bannedUser={} | entryCount={}",
          part.getGuildId().asString(),
          bannedId,
          part.getEntries().size());

      return Mono.justOrEmpty(
              part.getEntries().stream()
                  .filter(
                      entry ->
                          entry.getTargetId().map(Snowflake::asString).orElse("").equals(bannedId))
                  .findFirst())
          .flatMap(
              entry -> {
                log.debug(
                    "Matched audit entry | target={} | responsibleUser={} | reason={}",
                    entry.getTargetId().map(Snowflake::asString).orElse("N/A"),
                    entry.getResponsibleUserId().map(Snowflake::asString).orElse("N/A"),
                    entry.getReason().orElse("<empty>"));

                if (isMissingReason(entry.getReason().orElse(null))) {
                  log.debug(
                      "Ban reason missing/default, preparing reminder | channelId={} | bannedUser={}",
                      MOD_CHANNEL_ID.asString(),
                      event.getUser().getId().asString());

                  return entry
                      .getResponsibleUserId()
                      .map(
                          responsible ->
                              event
                                  .getGuild()
                                  .doOnNext(
                                      guild ->
                                          log.debug(
                                              "Sending reminder to channel | guild={} | channelId={} | moderator={}",
                                              guild.getId().asString(),
                                              MOD_CHANNEL_ID.asString(),
                                              responsible.asString()))
                                  .flatMap(guild -> guild.getChannelById(MOD_CHANNEL_ID))
                                  .cast(TextChannel.class)
                                  .doOnNext(
                                      channel ->
                                          log.debug(
                                              "Resolved reminder channel | name={} | id={}",
                                              channel.getName(),
                                              channel.getId().asString()))
                                  .flatMap(
                                      channel ->
                                          channel.createMessage(
                                              MessageCreateSpec.builder()
                                                  .content(
                                                      "<@"
                                                          + responsible.asString()
                                                          + "> you forgot to provide a ban reason for **"
                                                          + event.getUser().getTag()
                                                          + "**.")
                                                  .build())))
                      .orElseGet(
                          () -> {
                            log.debug(
                                "No responsible user id found in audit entry | guild={} | bannedUser={}",
                                part.getGuildId().asString(),
                                bannedId);
                            return Mono.empty();
                          });
                }

                log.debug(
                    "Ban reason present, no reminder needed | bannedUser={} | reason={}",
                    bannedId,
                    entry.getReason().orElse("<empty>"));
                return Mono.empty();
              })
          .switchIfEmpty(
              Mono.fromRunnable(
                  () ->
                      log.debug(
                          "No matching audit entry found for banned user | guild={} | bannedUser={}",
                          part.getGuildId().asString(),
                          bannedId)))
          .then();
    } catch (Exception exception) {
      log.error("Failed while processing audit part for ban event", exception);
      return Mono.empty();
    }
  }

  private boolean isMissingReason(String reason) {
    return reason == null || reason.isBlank() || reason.equalsIgnoreCase(DEFAULT_BAN_REASON);
  }
}
