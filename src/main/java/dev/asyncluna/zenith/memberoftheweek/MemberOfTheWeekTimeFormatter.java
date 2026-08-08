package dev.asyncluna.zenith.memberoftheweek;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class MemberOfTheWeekTimeFormatter {
  private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm z";

  private MemberOfTheWeekTimeFormatter() {}

  public static String format(Instant instant, ZoneId zoneId) {
    return DateTimeFormatter.ofPattern(DATE_PATTERN).withZone(zoneId).format(instant);
  }
}
