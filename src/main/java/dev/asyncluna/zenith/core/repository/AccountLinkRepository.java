package dev.asyncluna.zenith.core.repository;

import dev.asyncluna.zenith.core.model.AccountLink;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AccountLinkRepository extends ReactiveMongoRepository<AccountLink, String> {
  Mono<AccountLink> findByBattleTag(String battleTag);
}
