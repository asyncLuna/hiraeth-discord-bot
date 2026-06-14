package dev.asyncluna.owbot.core.integration.overfastapi;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;

@Builder
public record OverfastApiQueryParams(
    OverfastApiQueryParam.Role role,
    OverfastApiQueryParam.Locale locale,
    OverfastApiQueryParam.Gamemode gamemode) {
  public Map<String, String> asMap() {
    Map<String, String> map = new HashMap<>();
    if (role != null) map.put("role", role.toString());
    if (locale != null) map.put("locale", locale.toString());
    if (gamemode != null) map.put("gamemode", gamemode.toString());
    return map;
  }
}
