package dev.asyncluna.zenith.discord.command;

import discord4j.rest.util.Permission;
import lombok.Getter;

@Getter
public class MissingPermissionException extends RuntimeException {
    private final Permission permission;

    public MissingPermissionException(Permission permission) {
        super("Missing permission: " + permission.name(), null, false, false);
        this.permission = permission;
    }
}
