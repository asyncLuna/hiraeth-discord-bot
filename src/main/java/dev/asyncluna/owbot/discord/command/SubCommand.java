package dev.asyncluna.owbot.discord.command;

import discord4j.core.object.command.ApplicationCommandOption;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface SubCommand {
  String name();

  String description();

  ApplicationCommandOption.Type type();

  boolean required() default false;

  boolean autocomplete() default false;
}
