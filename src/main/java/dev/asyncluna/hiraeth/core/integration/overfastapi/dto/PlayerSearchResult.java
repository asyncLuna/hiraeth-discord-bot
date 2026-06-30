package dev.asyncluna.hiraeth.core.integration.overfastapi.dto;

import java.util.List;

public record PlayerSearchResult(Integer total, List<PlayerShort> results) {}
