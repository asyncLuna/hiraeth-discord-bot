package dev.asyncluna.zenith.memberoftheweek.model;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@Document("member_of_the_week_votes")
@CompoundIndex(
    name = "one_vote_per_member_per_round",
    def = "{'roundId': 1, 'voterId': 1}",
    unique = true)
public class MemberOfTheWeekVote {
  @Id private String id;

  private String roundId;
  private String guildId;
  private String voterId;
  private String candidateId;

  private Instant createdAt;
}
