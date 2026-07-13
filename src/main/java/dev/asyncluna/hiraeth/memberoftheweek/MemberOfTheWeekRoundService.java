package dev.asyncluna.hiraeth.memberoftheweek;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import dev.asyncluna.hiraeth.discord.util.EmbedUtils;
import dev.asyncluna.hiraeth.memberoftheweek.config.MemberOfTheWeekProperties;
import dev.asyncluna.hiraeth.memberoftheweek.model.MemberOfTheWeekRound;
import dev.asyncluna.hiraeth.memberoftheweek.model.MemberOfTheWeekRoundStatus;
import dev.asyncluna.hiraeth.memberoftheweek.model.MemberOfTheWeekVoteCount;
import dev.asyncluna.hiraeth.memberoftheweek.repository.MemberOfTheWeekRoundRepository;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.AllowedMentions;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberOfTheWeekRoundService {
  public static final String COMPONENT_ID_PREFIX = "member_of_the_week_vote:";

  private final GatewayDiscordClient gatewayDiscordClient;
  private final MemberOfTheWeekProperties properties;
  private final MemberOfTheWeekRoundRepository roundRepository;
  private final ReactiveMongoTemplate mongoTemplate;
  private final Clock memberOfTheWeekClock;

  public Mono<MemberOfTheWeekRound> rotateRound() {
    log.info("Rotating member-of-the-week voting round");

    return closeCurrentRound()
        .then(openNewRound())
        .doOnSuccess(
            round ->
                log.info(
                    "Member-of-the-week round rotated | roundId={} | endsAt={}",
                    round.getId(),
                    round.getEndsAt()))
        .doOnError(error -> log.error("Failed to rotate member-of-the-week round", error));
  }

  public Mono<MemberOfTheWeekRound> openInitialRound() {
    log.info("Opening initial member-of-the-week voting round");

    return openNewRound()
        .doOnSuccess(
            round ->
                log.info(
                    "Initial member-of-the-week round opened | roundId={} | endsAt={}",
                    round.getId(),
                    round.getEndsAt()));
  }

  private Mono<Void> closeCurrentRound() {
    return roundRepository
        .findFirstByGuildIdAndStatusOrderByStartsAtDesc(
            properties.guildId(), MemberOfTheWeekRoundStatus.OPEN)
        .flatMap(this::closeRound)
        .switchIfEmpty(
            Mono.defer(
                () -> {
                  log.info("No open member-of-the-week round found to close");
                  return Mono.empty();
                }))
        .then();
  }

  private Mono<MemberOfTheWeekRound> closeRound(MemberOfTheWeekRound round) {
    log.info("Closing member-of-the-week round | roundId={}", round.getId());

    return findWinners(round.getId())
        .flatMap(this::announceResults)
        .then(
            Mono.defer(
                () -> {
                  round.setStatus(MemberOfTheWeekRoundStatus.CLOSED);

                  return roundRepository.save(round);
                }))
        .doOnSuccess(
            closedRound ->
                log.info("Member-of-the-week round closed | roundId={}", closedRound.getId()));
  }

  private Mono<MemberOfTheWeekRound> openNewRound() {
    Instant startsAt = Instant.now(memberOfTheWeekClock);
    Instant endsAt = calculateNextDeadline();

    MemberOfTheWeekRound round =
        MemberOfTheWeekRound.builder()
            .guildId(properties.guildId())
            .channelId(properties.channelId())
            .startsAt(startsAt)
            .endsAt(endsAt)
            .status(MemberOfTheWeekRoundStatus.OPEN)
            .build();

    return roundRepository
        .save(round)
        .flatMap(
            savedRound ->
                sendVotingMessage(savedRound)
                    .flatMap(
                        message -> {
                          savedRound.setMessageId(message.getId().asString());

                          return roundRepository.save(savedRound);
                        })
                    .onErrorResume(
                        error -> {
                          log.error(
                              "Failed to send voting message; deleting newly created round | roundId={}",
                              savedRound.getId(),
                              error);

                          return roundRepository.delete(savedRound).then(Mono.error(error));
                        }));
  }

  private Mono<Message> sendVotingMessage(MemberOfTheWeekRound round) {
    SelectMenu selectMenu =
        SelectMenu.ofUser(COMPONENT_ID_PREFIX + round.getId(), Collections.emptyList())
            .withPlaceholder("Select a member to vote for")
            .withMinValues(1)
            .withMaxValues(1);

    EmbedCreateSpec embed =
        EmbedCreateSpec.builder()
            .color(EmbedUtils.DEFAULT_COLOR)
            .title("Member of the Week")
            .description(
                """
                            Vote for this week's **Member of the Week**!

                            You may vote only once.
                            You cannot vote for yourself or a bot.
                            Voting closes next Monday.
                            """)
            .timestamp(Instant.now(memberOfTheWeekClock))
            .build();

    MessageCreateSpec.Builder messageBuilder =
        MessageCreateSpec.builder().addEmbed(embed).addComponent(ActionRow.of(selectMenu));

    if (properties.roleId() != null && !properties.roleId().isBlank()) {
      Snowflake roleId = Snowflake.of(properties.roleId());

      messageBuilder
          .content("<@&" + roleId.asString() + ">")
          .allowedMentions(AllowedMentions.builder().allowRole(roleId).build());
    }

    return getVotingChannel()
        .flatMap(channel -> channel.createMessage(messageBuilder.build()))
        .doOnSuccess(
            createdMessage ->
                log.info(
                    "Member-of-the-week voting message sent | channel={} | message={}",
                    properties.channelId(),
                    createdMessage.getId().asString()));
  }

  private String createWinnerPingContent(List<MemberOfTheWeekVoteCount> winners) {
    if (winners.isEmpty()) {
      return null;
    }

    return winners.stream()
        .map(winner -> "<@" + winner.candidateId() + ">")
        .reduce((first, second) -> first + " " + second)
        .orElse(null);
  }

  private Mono<Void> announceResults(List<MemberOfTheWeekVoteCount> winners) {
    String description = createResultsDescription(winners);
    String content = createWinnerPingContent(winners);

    EmbedCreateSpec embed =
        EmbedCreateSpec.builder()
            .color(EmbedUtils.DEFAULT_COLOR)
            .title("Member of the Week results")
            .description(description)
            .timestamp(Instant.now(memberOfTheWeekClock))
            .build();

    MessageCreateSpec.Builder messageBuilder = MessageCreateSpec.builder().addEmbed(embed);

    if (content != null) {
      messageBuilder
          .content(content)
          .allowedMentions(
              AllowedMentions.builder()
                  .allowUser(
                      winners.stream()
                          .map(MemberOfTheWeekVoteCount::candidateId)
                          .map(Snowflake::of)
                          .toArray(Snowflake[]::new))
                  .build());
    }

    return getVotingChannel()
        .flatMap(channel -> channel.createMessage(messageBuilder.build()))
        .doOnSuccess(
            message ->
                log.info(
                    "Member-of-the-week results announced | message={}",
                    message.getId().asString()))
        .then();
  }

  private String createResultsDescription(List<MemberOfTheWeekVoteCount> winners) {
    if (winners.isEmpty()) {
      return "No valid votes were submitted this week.";
    }

    if (winners.size() == 1) {
      MemberOfTheWeekVoteCount winner = winners.getFirst();

      return "Congratulations <@"
          + winner.candidateId()
          + ">! You are the **Member of the Week** with **"
          + winner.votes()
          + "** "
          + (winner.votes() == 1 ? "vote" : "votes")
          + "!";
    }

    long voteCount = winners.getFirst().votes();

    String mentions =
        winners.stream()
            .map(winner -> "<@" + winner.candidateId() + ">")
            .reduce((first, second) -> first + ", " + second)
            .orElse("");

    return "This week's vote ended in a tie between "
        + mentions
        + ". Each member received **"
        + voteCount
        + "** "
        + (voteCount == 1 ? "vote" : "votes")
        + ".";
  }

  private Mono<List<MemberOfTheWeekVoteCount>> findWinners(String roundId) {
    Aggregation aggregation =
        newAggregation(
            match(Criteria.where("roundId").is(roundId)),
            group("candidateId").count().as("votes"),
            sort(Sort.Direction.DESC, "votes"));

    return mongoTemplate
        .aggregate(aggregation, "member_of_the_week_votes", Document.class)
        .map(
            document -> {
              String candidateId = document.getString("_id");

              Number votes = document.get("votes", Number.class);

              return new MemberOfTheWeekVoteCount(candidateId, votes.longValue());
            })
        .collectList()
        .map(this::onlyHighestVoteCounts);
  }

  private List<MemberOfTheWeekVoteCount> onlyHighestVoteCounts(
      List<MemberOfTheWeekVoteCount> results) {
    if (results.isEmpty()) {
      return List.of();
    }

    long highestVoteCount = results.getFirst().votes();

    return results.stream().filter(result -> result.votes() == highestVoteCount).toList();
  }

  private Instant calculateNextDeadline() {
    ZonedDateTime now = ZonedDateTime.now(memberOfTheWeekClock);

    ZonedDateTime deadline =
        now.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            .withHour(18)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);

    return deadline.toInstant();
  }

  private Mono<MessageChannel> getVotingChannel() {
    Snowflake channelId = Snowflake.of(properties.channelId());

    return gatewayDiscordClient
        .getChannelById(channelId)
        .ofType(MessageChannel.class)
        .switchIfEmpty(
            Mono.error(
                new IllegalStateException(
                    "Member-of-the-week channel does not exist or is not a message channel: "
                        + properties.channelId())));
  }
}
