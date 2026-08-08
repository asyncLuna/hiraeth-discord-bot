package dev.asyncluna.zenith.discord.util;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class NumberUtils {
  /**
   * @param num The double number to check.
   * @param min The minimum value, inclusive.
   * @param max The maximum value, inclusive.
   * @return {@code true} if {@code num} is between {@code min} and {@code max}, {@code false}
   *     otherwise.
   */
  public static boolean isBetween(double num, double min, double max) {
    return num >= min && num <= max;
  }
}
