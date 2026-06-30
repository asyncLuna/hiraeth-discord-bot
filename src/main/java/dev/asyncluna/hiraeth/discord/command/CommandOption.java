package dev.asyncluna.hiraeth.discord.command;

import discord4j.core.object.command.ApplicationCommandOption;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(CommandOptions.class)
public @interface CommandOption {
  String name();

  String description();

  ApplicationCommandOption.Type type();

  boolean required() default false;

  boolean autocomplete() default false;

  SubCommand[] subCommands() default {};
}
