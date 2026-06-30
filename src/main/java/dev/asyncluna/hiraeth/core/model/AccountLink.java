package dev.asyncluna.hiraeth.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "account_links")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountLink {
  @Id private String discordId;
  @Builder.Default private long createdAt = System.currentTimeMillis();
  private String battleTag;
}
