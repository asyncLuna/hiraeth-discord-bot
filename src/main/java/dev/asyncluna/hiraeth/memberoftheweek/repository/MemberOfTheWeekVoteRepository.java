package dev.asyncluna.hiraeth.memberoftheweek.repository;

import dev.asyncluna.hiraeth.memberoftheweek.model.MemberOfTheWeekVote;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface MemberOfTheWeekVoteRepository
    extends ReactiveMongoRepository<MemberOfTheWeekVote, String> {}
