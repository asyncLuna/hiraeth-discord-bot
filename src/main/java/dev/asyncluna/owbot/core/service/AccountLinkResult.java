package dev.asyncluna.owbot.core.service;

import dev.asyncluna.owbot.core.model.AccountLink;

public record AccountLinkResult(AccountLink link, boolean isNew) {}
