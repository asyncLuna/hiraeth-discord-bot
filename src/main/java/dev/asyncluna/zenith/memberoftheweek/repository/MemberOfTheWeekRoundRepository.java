package dev.asyncluna.zenith.memberoftheweek.repository;

import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRound;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRoundStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MemberOfTheWeekRoundRepository
    extends ReactiveMongoRepository<MemberOfTheWeekRound, String> {

  Mono<MemberOfTheWeekRound> findFirstByGuildIdAndStatusOrderByStartsAtDesc(
      String guildId, MemberOfTheWeekRoundStatus status);

  Mono<MemberOfTheWeekRound> findByIdAndGuildId(String id, String guildId);

  Flux<MemberOfTheWeekRound> findByGuildIdOrderByStartsAtDesc(String guildId);
}
