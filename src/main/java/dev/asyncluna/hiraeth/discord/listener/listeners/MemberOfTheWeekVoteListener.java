package dev.asyncluna.hiraeth.discord.listener.listeners;

import dev.asyncluna.hiraeth.discord.listener.EventListener;
import dev.asyncluna.hiraeth.memberoftheweek.MemberOfTheWeekRoundService;
import dev.asyncluna.hiraeth.memberoftheweek.MemberOfTheWeekVoteService;
import dev.asyncluna.hiraeth.memberoftheweek.exception.AlreadyVotedException;
import dev.asyncluna.hiraeth.memberoftheweek.exception.SelfVoteException;
import dev.asyncluna.hiraeth.memberoftheweek.exception.VotingRoundClosedException;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberOfTheWeekVoteListener implements EventListener<SelectMenuInteractionEvent> {
  private final MemberOfTheWeekVoteService voteService;

  @Override
  public Mono<Void> execute(SelectMenuInteractionEvent event) {
    String customId = event.getCustomId();

    if (!customId.startsWith(MemberOfTheWeekRoundService.COMPONENT_ID_PREFIX)) {
      return Mono.empty();
    }

    return event
        .deferReply()
        .withEphemeral(true)
        .then(handleVote(event))
        .onErrorResume(
            SelfVoteException.class,
            error -> event.editReply("You can't vote for yourself!").then())
        .onErrorResume(
            AlreadyVotedException.class,
            error -> event.editReply("You have already voted this week!").then())
        .onErrorResume(
            VotingRoundClosedException.class,
            error -> event.editReply("This voting round is no longer active.").then())
        .onErrorResume(
            MemberNotFoundException.class,
            error -> event.editReply("That member could not be found in this server.").then())
        .onErrorResume(
            InvalidSelectionException.class,
            error -> event.editReply("The selected member is invalid.").then())
        .doOnError(
            error -> log.error("Unexpected error while handling a member-of-the-week vote", error))
        .onErrorResume(
            error ->
                event
                    .editReply("Something went wrong while recording your vote.")
                    .onErrorResume(
                        replyError -> {
                          log.error(
                              "Failed to edit deferred member-of-the-week interaction response",
                              replyError);

                          return Mono.empty();
                        })
                    .then());
  }

  private Mono<Void> handleVote(SelectMenuInteractionEvent event) {
    if (event.getValues().isEmpty()) {
      return event.editReply("You must select a member to vote for!").then();
    }

    String roundId =
        event.getCustomId().substring(MemberOfTheWeekRoundService.COMPONENT_ID_PREFIX.length());

    Snowflake candidateId = parseCandidateId(event.getValues().getFirst());

    Snowflake voterId = event.getInteraction().getUser().getId();

    return event
        .getInteraction()
        .getGuild()
        .switchIfEmpty(
            Mono.error(new IllegalStateException("The interaction did not occur in a guild.")))
        .flatMap(
            guild ->
                guild
                    .getMemberById(candidateId)
                    .switchIfEmpty(Mono.error(new MemberNotFoundException()))
                    .flatMap(
                        candidate -> {
                          if (candidate.isBot()) {
                            return event.editReply("You can't vote for a bot!").then();
                          }

                          return voteService
                              .recordVote(
                                  roundId,
                                  guild.getId().asString(),
                                  voterId.asString(),
                                  candidateId.asString())
                              .then(
                                  event
                                      .editReply(
                                          "Your vote for **"
                                              + candidate.getDisplayName()
                                              + "** has been recorded!")
                                      .then());
                        }))
        .then();
  }

  private Snowflake parseCandidateId(String rawCandidateId) {
    try {
      return Snowflake.of(rawCandidateId);
    } catch (IllegalArgumentException exception) {
      throw new InvalidSelectionException(exception);
    }
  }

  private static final class MemberNotFoundException extends RuntimeException {}

  private static final class InvalidSelectionException extends RuntimeException {
    private InvalidSelectionException(Throwable cause) {
      super(cause);
    }
  }
}
