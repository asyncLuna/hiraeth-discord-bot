package dev.asyncluna.hiraeth.core.repository;

import dev.asyncluna.hiraeth.core.model.AccountLink;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AccountLinkRepository extends ReactiveMongoRepository<AccountLink, String> {
  Mono<AccountLink> findByBattleTag(String battleTag);
}
