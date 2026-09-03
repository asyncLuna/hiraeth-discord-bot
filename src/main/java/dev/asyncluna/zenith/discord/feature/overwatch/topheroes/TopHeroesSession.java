package dev.asyncluna.zenith.discord.feature.overwatch.topheroes;

import dev.asyncluna.zenith.core.integration.overfastapi.dto.PlayerCareerStats;

public record TopHeroesSession(
        String userId, String playerId, String mode, String statPath, PlayerCareerStats stats, int[] pageHolder) {
    public int page() {
        return pageHolder[0];
    }

    public void nextPage() {
        pageHolder[0]++;
    }

    public void previousPage() {
        pageHolder[0]--;
    }
}
