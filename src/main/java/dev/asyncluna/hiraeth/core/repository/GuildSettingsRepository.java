package dev.asyncluna.hiraeth.core.repository;

import dev.asyncluna.hiraeth.core.model.GuildSettings;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GuildSettingsRepository extends ReactiveMongoRepository<GuildSettings, String> {}
