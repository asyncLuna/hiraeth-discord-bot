package dev.asyncluna.zenith.core.integration.overfastapi.dto;

import java.util.List;

public record Hero(
        String name,
        String description,
        String portrait,
        List<HeroBackground> backgrounds,
        String role,
        String subrole,
        String location,
        Integer age,
        String birthday,
        HitPoints hitpoints,
        List<Ability> abilities,
        PerksContainer perks,
        Story story) {}
