package dev.asyncluna.zenith.discord.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Command {
    String name();

    String description();

    boolean ephemeral() default false;

    String defaultMemberPermissions() default "";

    boolean dmPermission() default false;
}
