package dev.asyncluna.hiraeth.memberoftheweek.repository;

import dev.asyncluna.hiraeth.memberoftheweek.model.MemberOfTheWeekRound;
import dev.asyncluna.hiraeth.memberoftheweek.model.MemberOfTheWeekRoundStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface MemberOfTheWeekRoundRepository
    extends ReactiveMongoRepository<MemberOfTheWeekRound, String> {

  Mono<MemberOfTheWeekRound> findFirstByGuildIdAndStatusOrderByStartsAtDesc(
      String guildId, MemberOfTheWeekRoundStatus status);
}
