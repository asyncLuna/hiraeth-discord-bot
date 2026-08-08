package dev.asyncluna.zenith.memberoftheweek.scheduler;

import dev.asyncluna.zenith.memberoftheweek.config.MemberOfTheWeekProperties;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MemberOfTheWeekQuartzConfiguration {
  @Bean
  public AutowiringSpringBeanJobFactory quartzJobFactory(ApplicationContext applicationContext) {
    AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
    jobFactory.setApplicationContext(applicationContext);
    return jobFactory;
  }

  @Bean
  public SchedulerFactoryBeanCustomizer memberOfTheWeekSchedulerCustomizer(
      AutowiringSpringBeanJobFactory jobFactory) {
    return schedulerFactoryBean -> schedulerFactoryBean.setJobFactory(jobFactory);
  }

  @Bean
  public JobDetail memberOfTheWeekJobDetail() {
    log.info("Registering Member of the Week Quartz job");

    return JobBuilder.newJob(MemberOfTheWeekRotationJob.class)
        .withIdentity("member-of-the-week-rotation-job")
        .withDescription("Closes the previous Member of the Week round and opens a new one")
        .storeDurably()
        .build();
  }

  @Bean
  public JobDetail memberOfTheWeekExpiryJobDetail() {
    log.info("Registering Member of the Week expiry Quartz job");

    return JobBuilder.newJob(MemberOfTheWeekExpiryJob.class)
        .withIdentity("member-of-the-week-expiry-job")
        .withDescription("Closes an expired Member of the Week round")
        .storeDurably()
        .build();
  }

  @Bean
  public CronTrigger memberOfTheWeekTrigger(
      @Qualifier("memberOfTheWeekJobDetail") JobDetail memberOfTheWeekJobDetail,
      MemberOfTheWeekProperties properties) {
    TimeZone timezone = TimeZone.getTimeZone(properties.timezone());

    CronScheduleBuilder schedule =
        CronScheduleBuilder.cronSchedule(properties.cron())
            .inTimeZone(timezone)
            .withMisfireHandlingInstructionFireAndProceed();

    CronTrigger trigger =
        TriggerBuilder.newTrigger()
            .withIdentity("member-of-the-week-monday-trigger")
            .forJob(memberOfTheWeekJobDetail)
            .withSchedule(schedule)
            .build();

    log.info(
        "Registered Member of the Week Quartz trigger | cron={} | timezone={}",
        properties.cron(),
        timezone.getID());

    return trigger;
  }

  @Bean
  public Trigger memberOfTheWeekExpiryTrigger(
      @Qualifier("memberOfTheWeekExpiryJobDetail") JobDetail memberOfTheWeekExpiryJobDetail) {
    Trigger trigger =
        TriggerBuilder.newTrigger()
            .withIdentity("member-of-the-week-expiry-trigger")
            .forJob(memberOfTheWeekExpiryJobDetail)
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule().withIntervalInMinutes(1).repeatForever())
            .startNow()
            .build();

    log.info("Registered Member of the Week expiry Quartz trigger | interval=1 minute");

    return trigger;
  }
}
