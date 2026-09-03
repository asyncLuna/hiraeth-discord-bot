package dev.asyncluna.zenith.discord.command;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface CommandChoice {
    String name();

    String value();
}
