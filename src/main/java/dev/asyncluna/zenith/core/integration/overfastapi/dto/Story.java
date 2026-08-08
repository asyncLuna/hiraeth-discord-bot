package dev.asyncluna.zenith.core.integration.overfastapi.dto;

import java.util.List;

public record Story(String summary, Media media, List<StoryChapter> chapters) {}
