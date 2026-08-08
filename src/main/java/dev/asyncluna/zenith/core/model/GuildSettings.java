package dev.asyncluna.zenith.core.model;

import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "guild_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuildSettings {
  @Id private String id;
  @Builder.Default private String locale = SupportedLocale.ENGLISH.getLocale().toLanguageTag();
  @Builder.Default private long createdAt = System.currentTimeMillis();
  @Builder.Default private boolean confessionsEnabled = true;
  @Builder.Default private boolean memberOfTheWeekPaused = false;
  private String confessionsChannelId;
  private String confessionsLogChannelId;
  private String memberOfTheWeekChannelId;
  private String memberOfTheWeekLogChannelId;
  private String moderationLogChannelId;
}
