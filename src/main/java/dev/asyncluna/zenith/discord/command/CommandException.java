package dev.asyncluna.zenith.discord.command;

public class CommandException extends RuntimeException {
    public CommandException(String message) {
        super(message, null, false, false);
    }
}
