package dev.asyncluna.zenith.core.integration.overfastapi.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class PlayerCareerStats {
    private final JsonNode heroes;

    public PlayerCareerStats(JsonNode heroes) {
        this.heroes = heroes;
    }

    public Map<String, JsonNode> heroes() {
        if (heroes == null || !heroes.isObject()) return Map.of();
        Map<String, JsonNode> result = new LinkedHashMap<>();
        heroes.properties().forEach(field -> result.put(field.getKey(), field.getValue()));
        return result;
    }
}
