package dev.asyncluna.zenith.core.integration.overfastapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Map(
    String key,
    String name,
    String screenshot,
    List<String> gamemodes,
    String location,
    @JsonProperty("country_code") String countryCode) {}
