package dev.asyncluna.zenith.memberoftheweek;

import java.time.Instant;
import java.time.ZoneId;

public final class MemberOfTheWeekTimeFormatter {
  private MemberOfTheWeekTimeFormatter() {}

  public static String format(Instant instant, ZoneId zoneId) {
    return "<t:" + instant.getEpochSecond() + ":R>";
  }
}
