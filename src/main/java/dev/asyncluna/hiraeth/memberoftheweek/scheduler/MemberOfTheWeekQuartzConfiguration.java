package dev.asyncluna.hiraeth.memberoftheweek.scheduler;

import dev.asyncluna.hiraeth.memberoftheweek.config.MemberOfTheWeekProperties;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.TriggerBuilder;
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
    log.info("Registering member-of-the-week Quartz job");

    return JobBuilder.newJob(MemberOfTheWeekRotationJob.class)
        .withIdentity("member-of-the-week-rotation-job")
        .withDescription("Closes the previous member-of-the-week round and opens a new one")
        .storeDurably()
        .build();
  }

  @Bean
  public CronTrigger memberOfTheWeekTrigger(
      JobDetail memberOfTheWeekJobDetail, MemberOfTheWeekProperties properties) {
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
        "Registered member-of-the-week Quartz trigger | cron={} | timezone={}",
        properties.cron(),
        timezone.getID());

    return trigger;
  }
}
