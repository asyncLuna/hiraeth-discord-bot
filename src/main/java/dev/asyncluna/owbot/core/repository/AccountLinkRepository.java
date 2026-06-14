package dev.asyncluna.owbot.core.repository;

import dev.asyncluna.owbot.core.model.AccountLink;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AccountLinkRepository extends ReactiveMongoRepository<AccountLink, String> {
  Mono<AccountLink> findByBattleTag(String battleTag);
}
