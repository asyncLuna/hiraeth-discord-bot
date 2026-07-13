package dev.asyncluna.hiraeth.memberoftheweek.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MemberOfTheWeekProperties.class)
public class MemberOfTheWeekConfiguration {
  @Bean
  public Clock memberOfTheWeekClock(MemberOfTheWeekProperties properties) {
    return Clock.system(ZoneId.of(properties.timezone()));
  }
}
