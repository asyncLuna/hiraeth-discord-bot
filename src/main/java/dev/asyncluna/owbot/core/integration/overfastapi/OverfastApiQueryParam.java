package dev.asyncluna.owbot.core.integration.overfastapi;

import io.sentry.util.StringUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OverfastApiQueryParam {
  public enum Role {
    DAMAGE,
    SUPPORT,
    TANK;

    public static Role fromApiName(String name) {
      return Role.valueOf(name.toUpperCase());
    }

    @Override
    public String toString() {
      return name().toLowerCase();
    }

    public String getFriendlyName() {
      return StringUtils.capitalize(toString());
    }
  }

  public enum Locale {
    DE_DE,
    EN_GB,
    EN_US,
    ES_ES,
    ES_MX,
    FR_FR,
    IT_IT,
    JA_JP,
    KO_KR,
    PL_PL,
    PT_BR,
    RU_RU,
    ZH_TW;

    public static Locale fromApiName(String name) {
      return Locale.valueOf(name.toUpperCase().replace('-', '_'));
    }

    @Override
    public String toString() {
      return name().toLowerCase().replace("_", "-");
    }

    public String getFriendlyName() {
      return switch (this) {
        case DE_DE -> "German (Germany)";
        case EN_GB -> "English (United Kingdom)";
        case EN_US -> "English (United States)";
        case ES_ES -> "Spanish (Spain)";
        case ES_MX -> "Spanish (Mexico)";
        case FR_FR -> "French (France)";
        case IT_IT -> "Italian (Italy)";
        case JA_JP -> "Japanese (Japan)";
        case KO_KR -> "Korean (South Korea)";
        case PL_PL -> "Polish (Poland)";
        case PT_BR -> "Portuguese (Brazil)";
        case RU_RU -> "Russian (Russia)";
        case ZH_TW -> "Chinese (Taiwan)";
      };
    }
  }

  public enum Platform {
    PC,
    CONSOLE;

    public static Platform fromApiName(String name) {
      return Platform.valueOf(name.toUpperCase());
    }

    @Override
    public String toString() {
      return name().toLowerCase();
    }

    public String getFriendlyName() {
      return switch (this) {
        case PC -> "PC";
        case CONSOLE -> "Console";
      };
    }
  }

  public enum Region {
    EUROPE,
    AMERICAS,
    ASIA;

    public static Region fromApiName(String name) {
      return Region.valueOf(name.toUpperCase());
    }

    @Override
    public String toString() {
      return name().toLowerCase();
    }

    public String getFriendlyName() {
      return StringUtils.capitalize(toString());
    }
  }

  public enum CompetitiveDivision {
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM,
    DIAMOND,
    MASTER,
    GRANDMASTER;

    public static CompetitiveDivision fromApiName(String name) {
      return CompetitiveDivision.valueOf(name.toUpperCase());
    }

    @Override
    public String toString() {
      return name().toLowerCase();
    }

    public String getFriendlyName() {
      return StringUtils.capitalize(toString());
    }
  }

  public enum OrderBy {
    HERO_ASC,
    HERO_DESC,
    WINRATE_ASC,
    WINRATE_DESC,
    PICKRATE_ASC,
    PICKRATE_DESC;

    public static OrderBy fromApiName(String name) {
      return OrderBy.valueOf(name.toUpperCase().replace(":", "_"));
    }

    @Override
    public String toString() {
      return name().toLowerCase().replace("_", ":");
    }

    public String getFriendlyName() {
      return switch (this) {
        case HERO_ASC -> "Hero Name (A-Z)";
        case HERO_DESC -> "Hero Name (Z-A)";
        case WINRATE_ASC -> "Win Rate (Low to High)";
        case WINRATE_DESC -> "Win Rate (High to Low)";
        case PICKRATE_ASC -> "Pick Rate (Low to High)";
        case PICKRATE_DESC -> "Pick Rate (High to Low)";
      };
    }
  }
}
