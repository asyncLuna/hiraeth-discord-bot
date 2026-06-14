package dev.asyncluna.owbot.discord.util;

import discord4j.core.spec.EmbedCreateFields;
import discord4j.rest.util.Color;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class EmbedUtils {
  public static final Color DEFAULT_COLOR = Color.of(255, 156, 0);
  public static final Color ERROR_COLOR = Color.of(255, 0, 0);
  public static final EmbedCreateFields.Field EMPTY_FIELD =
      EmbedCreateFields.Field.of("", "", false);
}
