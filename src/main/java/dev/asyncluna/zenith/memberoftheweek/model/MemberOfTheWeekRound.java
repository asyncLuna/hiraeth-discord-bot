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
@Document("member_of_the_week_rounds")
@CompoundIndex(name = "one_open_round_per_guild_lookup", def = "{'guildId': 1, 'status': 1}")
public class MemberOfTheWeekRound {
  @Id private String id;

  private String guildId;
  private String channelId;
  private String messageId;

  private Instant startsAt;
  private Instant endsAt;

  private MemberOfTheWeekRoundStatus status;
}
