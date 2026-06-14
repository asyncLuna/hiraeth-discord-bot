package dev.asyncluna.owbot.core.integration.overfastapi;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;

@Builder
public record OverfastApiQueryParams(
    OverfastApiQueryParam.Role role,
    OverfastApiQueryParam.Locale locale,
    String gamemode,
    OverfastApiQueryParam.Platform platform,
    OverfastApiQueryParam.Region region,
    String map,
    OverfastApiQueryParam.CompetitiveDivision competitiveDivision,
    OverfastApiQueryParam.OrderBy orderBy) {
  public Map<String, String> asMap() {
    Map<String, String> result = new HashMap<>();

    if (role != null) result.put("role", role.toString());
    if (locale != null) result.put("locale", locale.toString());
    if (gamemode != null) result.put("gamemode", gamemode);
    if (platform != null) result.put("platform", platform.toString());
    if (region != null) result.put("region", region.toString());
    if (map != null) result.put("map", map);
    if (competitiveDivision != null)
      result.put("competitiveDivision", competitiveDivision.toString());
    if (orderBy != null) result.put("orderBy", orderBy.toString());

    return result;
  }
}
