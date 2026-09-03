package dev.asyncluna.zenith.core.service;

import dev.asyncluna.zenith.core.exception.AccountAlreadyLinkedException;
import dev.asyncluna.zenith.core.model.AccountLink;
import dev.asyncluna.zenith.core.repository.AccountLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AccountLinkService {
    private final AccountLinkRepository repository;

    public Mono<AccountLinkResult> link(String discordId, String battleTag) {
        return repository
                .findByBattleTag(battleTag)
                .flatMap(existingLink -> {
                    if (!discordId.equals(existingLink.getDiscordId())) {
                        return Mono.error(new AccountAlreadyLinkedException());
                    }
                    return Mono.just(new AccountLinkResult(existingLink, false));
                })
                .switchIfEmpty(Mono.defer(() -> repository
                        .findById(discordId)
                        .flatMap(existingLink -> {
                            existingLink.setBattleTag(battleTag);
                            return repository
                                    .save(existingLink)
                                    .map(savedLink -> new AccountLinkResult(savedLink, true));
                        })
                        .switchIfEmpty(Mono.defer(() -> repository
                                .save(AccountLink.builder()
                                        .discordId(discordId)
                                        .battleTag(battleTag)
                                        .build())
                                .map(savedLink -> new AccountLinkResult(savedLink, true))))));
    }
}
