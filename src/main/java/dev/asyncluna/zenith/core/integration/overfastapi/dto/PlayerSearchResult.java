package dev.asyncluna.zenith.core.integration.overfastapi.dto;

import java.util.List;

public record PlayerSearchResult(Integer total, List<PlayerShort> results) {}
