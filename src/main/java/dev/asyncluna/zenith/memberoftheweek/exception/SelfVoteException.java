package dev.asyncluna.zenith.memberoftheweek.exception;

public class SelfVoteException extends RuntimeException {
    public SelfVoteException() {
        super("Members cannot vote for themselves.");
    }
}
