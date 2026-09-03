package dev.asyncluna.zenith.memberoftheweek;

import dev.asyncluna.zenith.memberoftheweek.exception.AlreadyVotedException;
import dev.asyncluna.zenith.memberoftheweek.exception.SelfVoteException;
import dev.asyncluna.zenith.memberoftheweek.exception.VotingRoundClosedException;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRoundStatus;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekVote;
import dev.asyncluna.zenith.memberoftheweek.repository.MemberOfTheWeekRoundRepository;
import dev.asyncluna.zenith.memberoftheweek.repository.MemberOfTheWeekVoteRepository;
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
    private final Clock memberOfTheWeekClock;
    private final MemberOfTheWeekDiscordNotifier discordNotifier;

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
                .flatMap(round -> voteRepository.save(MemberOfTheWeekVote.builder()
                        .roundId(round.getId())
                        .guildId(guildId)
                        .voterId(voterId)
                        .candidateId(candidateId)
                        .createdAt(now)
                        .build()))
                .onErrorMap(DuplicateKeyException.class, error -> new AlreadyVotedException())
                .flatMap(vote -> discordNotifier
                        .sendVoteLog(vote)
                        .onErrorResume(error -> {
                            log.error(
                                    "Vote was saved, but the Discord vote log failed | round={} | voter={} | candidate={}",
                                    vote.getRoundId(),
                                    vote.getVoterId(),
                                    vote.getCandidateId(),
                                    error);

                            return Mono.empty();
                        })
                        .thenReturn(vote))
                .doOnSuccess(vote -> log.info(
                        "Member of the Week vote recorded | round={} | voter={} | candidate={}",
                        vote.getRoundId(),
                        vote.getVoterId(),
                        vote.getCandidateId()))
                .then();
    }
}
