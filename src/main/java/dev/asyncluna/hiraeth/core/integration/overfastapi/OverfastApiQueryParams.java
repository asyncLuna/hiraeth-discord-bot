package dev.asyncluna.hiraeth.core.integration.overfastapi;

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
    OverfastApiQueryParam.OrderBy orderBy,
    String heroKey) {
  public Map<String, String> asMap() {
    Map<String, String> result = new HashMap<>();

    if (role != null) result.put("role", role.toString());
    if (locale != null) result.put("locale", locale.toString());
    if (gamemode != null) result.put("gamemode", gamemode);
    if (platform != null) result.put("platform", platform.toString());
    if (region != null) result.put("region", region.toString());
    if (map != null) result.put("map", map);
    if (competitiveDivision != null)
      result.put("competitive_division", competitiveDivision.toString());
    if (orderBy != null) result.put("order_by", orderBy.toString());
    if (heroKey != null) result.put("hero_key", heroKey);

    return result;
  }
}
