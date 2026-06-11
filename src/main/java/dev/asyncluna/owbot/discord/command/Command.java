package dev.asyncluna.owbot.discord.command;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Command {
  String name();

  String description();

  String defaultMemberPermissions() default "0";

  boolean dmPermission() default false;
}
