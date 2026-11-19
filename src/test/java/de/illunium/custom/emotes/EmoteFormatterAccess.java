package de.illunium.custom.emotes;

/** Test access to the package-private default table for tests in other packages. */
public final class EmoteFormatterAccess {
    private EmoteFormatterAccess() {
    }

    public static EmoteFormatter defaults() {
        return new EmoteFormatter(EmoteFormatter.DEFAULTS);
    }
}
