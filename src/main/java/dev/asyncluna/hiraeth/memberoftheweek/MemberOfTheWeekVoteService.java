package dev.asyncluna.hiraeth.memberoftheweek;

import dev.asyncluna.hiraeth.discord.util.EmbedUtils;
import dev.asyncluna.hiraeth.memberoftheweek.config.MemberOfTheWeekProperties;
import dev.asyncluna.hiraeth.memberoftheweek.exception.AlreadyVotedException;
import dev.asyncluna.hiraeth.memberoftheweek.exception.SelfVoteException;
import dev.asyncluna.hiraeth.memberoftheweek.exception.VotingRoundClosedException;
import dev.asyncluna.hiraeth.memberoftheweek.model.MemberOfTheWeekRoundStatus;
import dev.asyncluna.hiraeth.memberoftheweek.model.MemberOfTheWeekVote;
import dev.asyncluna.hiraeth.memberoftheweek.repository.MemberOfTheWeekRoundRepository;
import dev.asyncluna.hiraeth.memberoftheweek.repository.MemberOfTheWeekVoteRepository;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.AllowedMentions;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberOfTheWeekVoteService {
  private final MemberOfTheWeekRoundRepository roundRepository;
  private final MemberOfTheWeekVoteRepository voteRepository;
  private final GatewayDiscordClient gatewayDiscordClient;
  private final MemberOfTheWeekProperties properties;
  private final Clock memberOfTheWeekClock;

  public Mono<Void> recordVote(String roundId, String guildId, String voterId, String candidateId) {
    if (voterId.equals(candidateId)) {
      return Mono.error(new SelfVoteException());
    }

    Instant now = Instant.now(memberOfTheWeekClock);

    return roundRepository
        .findById(roundId)
        .filter(round -> round.getGuildId().equals(guildId))
        .filter(round -> round.getStatus() == MemberOfTheWeekRoundStatus.OPEN)
        .filter(round -> round.getStartsAt() != null)
        .filter(round -> round.getEndsAt() != null)
        .filter(round -> !now.isBefore(round.getStartsAt()))
        .filter(round -> now.isBefore(round.getEndsAt()))
        .switchIfEmpty(Mono.error(new VotingRoundClosedException()))
        .flatMap(
            round ->
                voteRepository.save(
                    MemberOfTheWeekVote.builder()
                        .roundId(round.getId())
                        .guildId(guildId)
                        .voterId(voterId)
                        .candidateId(candidateId)
                        .createdAt(now)
                        .build()))
        .onErrorMap(DuplicateKeyException.class, error -> new AlreadyVotedException())
        .flatMap(
            vote ->
                sendVoteLog(vote)
                    .onErrorResume(
                        error -> {
                          log.error(
                              "Vote was saved, but the Discord vote log failed | round={} | voter={} | candidate={}",
                              vote.getRoundId(),
                              vote.getVoterId(),
                              vote.getCandidateId(),
                              error);

                          return Mono.empty();
                        })
                    .thenReturn(vote))
        .doOnSuccess(
            vote ->
                log.info(
                    "Member-of-the-week vote recorded | round={} | voter={} | candidate={}",
                    vote.getRoundId(),
                    vote.getVoterId(),
                    vote.getCandidateId()))
        .then();
  }

  private Mono<Void> sendVoteLog(MemberOfTheWeekVote vote) {
    if (properties.logChannelId() == null || properties.logChannelId().isBlank()) {
      log.warn("Member-of-the-week vote log channel is not configured");

      return Mono.empty();
    }

    Snowflake logChannelId;

    try {
      logChannelId = Snowflake.of(properties.logChannelId());
    } catch (IllegalArgumentException exception) {
      return Mono.error(
          new IllegalStateException(
              "Invalid member-of-the-week log channel ID: " + properties.logChannelId(),
              exception));
    }

    Snowflake voterId = Snowflake.of(vote.getVoterId());
    Snowflake candidateId = Snowflake.of(vote.getCandidateId());

    EmbedCreateSpec embed =
        EmbedCreateSpec.builder()
            .color(EmbedUtils.DEFAULT_COLOR)
            .title("Member of the Week vote recorded")
            .addField("Voter", "<@" + vote.getVoterId() + ">", true)
            .addField("Candidate", "<@" + vote.getCandidateId() + ">", true)
            .addField("Round", "`" + vote.getRoundId() + "`", false)
            .timestamp(vote.getCreatedAt())
            .build();

    MessageCreateSpec message =
        MessageCreateSpec.builder()
            .addEmbed(embed)
            .allowedMentions(AllowedMentions.builder().allowUser(voterId, candidateId).build())
            .build();

    return gatewayDiscordClient
        .getChannelById(logChannelId)
        .ofType(MessageChannel.class)
        .switchIfEmpty(
            Mono.error(
                new IllegalStateException(
                    "Member-of-the-week log channel does not exist or is not a message channel: "
                        + properties.logChannelId())))
        .flatMap(channel -> channel.createMessage(message))
        .doOnSuccess(
            createdMessage ->
                log.info(
                    "Member-of-the-week vote log sent | channel={} | message={}",
                    properties.logChannelId(),
                    createdMessage.getId().asString()))
        .then();
  }
}
