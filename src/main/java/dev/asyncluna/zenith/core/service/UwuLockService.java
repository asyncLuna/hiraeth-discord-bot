package dev.asyncluna.zenith.core.service;

import dev.asyncluna.zenith.core.model.UwuLock;
import dev.asyncluna.zenith.core.repository.UwuLockRepository;
import dev.asyncluna.zenith.core.util.Uwuifier;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.Webhook;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.core.object.entity.channel.TopLevelGuildMessageChannel;
import discord4j.core.spec.WebhookCreateSpec;
import discord4j.core.spec.WebhookExecuteSpec;
import discord4j.rest.util.AllowedMentions;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UwuLockService {
  private final UwuLockRepository uwuLockRepository;
  private final Uwuifier uwuifier;
  private final Set<String> lockedUserIds = ConcurrentHashMap.newKeySet();

  private final Pattern urlPattern =
      Pattern.compile(
          "^(https?://)?([a-zA-Z0-9\\-_]+\\.)+[a-zA-Z]{2,}(/[\\w\\-_~:/?#\\[\\]@!$&'()*+,;=.\\u00A0-\\uFFFF]*)?$");

  @EventListener(ApplicationReadyEvent.class)
  public void loadLocksIntoMemory() {
    uwuLockRepository.findAll().map(UwuLock::discordId).doOnNext(lockedUserIds::add).subscribe();
  }

  public Mono<Void> handleMessageCreate(MessageCreateEvent event) {
    if (event.getGuildId().isEmpty() || event.getMember().isEmpty()) return Mono.empty();

    Member member = event.getMember().get();
    String userId = member.getId().asString();

    if (!lockedUserIds.contains(userId)) return Mono.empty();

    return processUwuLock(event, member);
  }

  public Mono<Boolean> lockUser(String userId) {
    if (lockedUserIds.contains(userId)) return Mono.just(false);
    return uwuLockRepository
        .save(new UwuLock(userId))
        .doOnSuccess(saved -> lockedUserIds.add(userId))
        .thenReturn(true);
  }

  public Mono<Boolean> unlockUser(String userId) {
    if (!lockedUserIds.contains(userId)) return Mono.just(false);
    return uwuLockRepository
        .deleteById(userId)
        .then(Mono.fromRunnable(() -> lockedUserIds.remove(userId)))
        .thenReturn(true);
  }

  public Flux<String> getLockedUsers() {
    return Flux.fromIterable(lockedUserIds);
  }

  private Mono<Void> processUwuLock(MessageCreateEvent event, Member member) {
    Message message = event.getMessage();

    return message
        .getChannel()
        .flatMap(
            channel -> {
              Mono<TopLevelGuildMessageChannel> parentChannelMono;
              Optional<Snowflake> threadId;

              if (channel instanceof ThreadChannel thread) {
                parentChannelMono = thread.getParent().cast(TopLevelGuildMessageChannel.class);
                threadId = Optional.of(thread.getId());
              } else if (channel instanceof TopLevelGuildMessageChannel guildChannel) {
                parentChannelMono = Mono.just(guildChannel);
                threadId = Optional.empty();
              } else return Mono.empty();

              return parentChannelMono.flatMap(
                  parentChannel ->
                      message
                          .delete()
                          .then(getOrCreateWebhook(parentChannel))
                          .flatMap(
                              webhook -> {
                                WebhookExecuteSpec.Builder specBuilder =
                                    WebhookExecuteSpec.builder()
                                        .username(member.getDisplayName())
                                        .avatarUrl(member.getAvatarUrl())
                                        .content(determineMessageContent(message))
                                        .allowedMentions(AllowedMentions.builder().build());

                                threadId.ifPresent(specBuilder::threadId);

                                return webhook.execute(specBuilder.build());
                              }));
            })
        .then();
  }

  private String determineMessageContent(Message message) {
    String rawContent = message.getContent().trim();

    boolean hasNativeMedia =
        !message.getAttachments().isEmpty() || !message.getStickersItems().isEmpty();

    boolean isOnlyLinks = isPurelyLinks(rawContent);

    if (rawContent.isEmpty() || hasNativeMedia || isOnlyLinks) return uwuifier.getRandomMessage();

    return uwuifier.uwuify(rawContent);
  }

  private boolean isPurelyLinks(String content) {
    if (content == null || content.isEmpty()) return false;

    String[] tokens = content.split("\\s+");
    if (tokens.length == 0) return false;

    for (String token : tokens) {
      if (!urlPattern.matcher(token).matches()) return false;
    }

    return true;
  }

  private Mono<Webhook> getOrCreateWebhook(TopLevelGuildMessageChannel channel) {
    return channel
        .getWebhooks()
        .filter(
            webhook ->
                webhook.getCreator().isPresent()
                    && webhook.getCreator().get().getId().equals(channel.getClient().getSelfId()))
        .next()
        .switchIfEmpty(
            channel.createWebhook(WebhookCreateSpec.builder().name("Zenith-UwuLock").build()));
  }
}
