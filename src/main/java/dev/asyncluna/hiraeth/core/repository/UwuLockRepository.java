package dev.asyncluna.hiraeth.core.repository;

import dev.asyncluna.hiraeth.core.model.UwuLock;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UwuLockRepository extends ReactiveMongoRepository<UwuLock, String> {}
