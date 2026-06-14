package dev.asyncluna.owbot.core.integration.overfastapi.dto;

import java.util.List;

public record HeroShort(
    String key,
    String name,
    String portrait,
    String role,
    String subrole,
    List<String> gamemodes) {}
