package dev.asyncluna.hiraeth.memberoftheweek.exception;

public class VotingRoundClosedException extends RuntimeException {
  public VotingRoundClosedException() {
    super("The voting round is not active.");
  }
}
