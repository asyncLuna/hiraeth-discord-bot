package dev.asyncluna.owbot.core.integration.overfastapi.dto;

public record TotalStatsSummary(
    int eliminations, int assists, int deaths, int damage, int healing) {}
