package dev.asyncluna.hiraeth.discord.command;

public class CommandException extends RuntimeException {
  public CommandException(String message) {
    super(message, null, false, false);
  }
}
