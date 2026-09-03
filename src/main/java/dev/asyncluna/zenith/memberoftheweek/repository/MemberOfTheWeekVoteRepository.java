package dev.asyncluna.zenith.memberoftheweek.repository;

import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekVote;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface MemberOfTheWeekVoteRepository extends ReactiveMongoRepository<MemberOfTheWeekVote, String> {}
