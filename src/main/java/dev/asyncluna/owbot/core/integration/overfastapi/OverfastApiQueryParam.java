package dev.asyncluna.owbot.core.integration.overfastapi;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OverfastApiQueryParam {
  public enum Role {
    DAMAGE,
    SUPPORT,
    TANK;

    @Override
    public String toString() {
      return name().toLowerCase();
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

    @Override
    public String toString() {
      return name().toLowerCase().replace("_", "-");
    }
  }

  public enum Gamemode {
    QUICK_PLAY,
    STADIUM,
    COMPETITIVE;

    @Override
    public String toString() {
      return name().toLowerCase().replace("_", "");
    }
  }
}
