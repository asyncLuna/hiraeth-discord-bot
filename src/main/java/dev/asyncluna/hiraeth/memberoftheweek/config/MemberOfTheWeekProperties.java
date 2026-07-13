package dev.asyncluna.hiraeth.memberoftheweek.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hiraeth.member-of-the-week")
public record MemberOfTheWeekProperties(
    String guildId,
    String channelId,
    String logChannelId,
    String roleId,
    String timezone,
    String cron) {}
