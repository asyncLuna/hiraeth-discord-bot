package dev.asyncluna.owbot.core.integration.overfastapi.dto;

import java.util.List;

public record PlayerSearchResult(int total, List<PlayerShort> results) {}
