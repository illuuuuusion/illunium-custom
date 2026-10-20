package de.illunium.custom.core.command;

/** A rule violation whose message is shown to the command sender as-is; nothing was changed. */
public final class UserException extends RuntimeException {

    public UserException(String message) {
        super(message, null, false, false);
    }
}
