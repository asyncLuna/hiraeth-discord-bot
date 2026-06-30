package dev.asyncluna.hiraeth.core.integration.overfastapi.dto;

import java.util.List;

public record Story(String summary, Media media, List<StoryChapter> chapters) {}
