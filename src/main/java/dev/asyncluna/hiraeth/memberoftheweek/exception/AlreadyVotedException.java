package dev.asyncluna.hiraeth.memberoftheweek.exception;

public class AlreadyVotedException extends RuntimeException {
  public AlreadyVotedException() {
    super("The member has already voted in this round.");
  }
}
