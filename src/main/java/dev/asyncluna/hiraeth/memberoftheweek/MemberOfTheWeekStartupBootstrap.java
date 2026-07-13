package dev.asyncluna.hiraeth.memberoftheweek;

import dev.asyncluna.hiraeth.memberoftheweek.config.MemberOfTheWeekProperties;
import dev.asyncluna.hiraeth.memberoftheweek.model.MemberOfTheWeekRound;
import dev.asyncluna.hiraeth.memberoftheweek.model.MemberOfTheWeekRoundStatus;
import dev.asyncluna.hiraeth.memberoftheweek.repository.MemberOfTheWeekRoundRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberOfTheWeekStartupBootstrap {
  private final MemberOfTheWeekProperties properties;
  private final MemberOfTheWeekRoundRepository roundRepository;
  private final MemberOfTheWeekRoundService roundService;
  private final Clock memberOfTheWeekClock;

  @EventListener(ApplicationReadyEvent.class)
  public void initializeVotingRound() {
    log.info("Checking for an active member-of-the-week voting round");

    roundRepository
        .findFirstByGuildIdAndStatusOrderByStartsAtDesc(
            properties.guildId(), MemberOfTheWeekRoundStatus.OPEN)
        .flatMap(this::handleExistingRound)
        .switchIfEmpty(
            Mono.defer(
                () -> {
                  log.info("No open member-of-the-week round found; opening one now");

                  return roundService.openInitialRound();
                }))
        .doOnSuccess(
            round ->
                log.info(
                    "Member-of-the-week startup check completed | roundId={} | endsAt={}",
                    round.getId(),
                    round.getEndsAt()))
        .doOnError(error -> log.error("Failed to initialize member-of-the-week voting", error))
        .subscribe();
  }

  private Mono<MemberOfTheWeekRound> handleExistingRound(MemberOfTheWeekRound round) {
    Instant now = Instant.now(memberOfTheWeekClock);

    if (round.getEndsAt() != null && round.getEndsAt().isAfter(now)) {

      log.info(
          "Active member-of-the-week round already exists | roundId={} | endsAt={}",
          round.getId(),
          round.getEndsAt());

      return Mono.just(round);
    }

    log.info(
        "Open member-of-the-week round has expired; rotating it now | roundId={} | endsAt={}",
        round.getId(),
        round.getEndsAt());

    return roundService.rotateRound();
  }
}
