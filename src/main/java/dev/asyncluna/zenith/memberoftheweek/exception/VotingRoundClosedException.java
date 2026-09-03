package dev.asyncluna.zenith.memberoftheweek.exception;

public class VotingRoundClosedException extends RuntimeException {
    public VotingRoundClosedException() {
        super("The voting round is not active.");
    }
}
