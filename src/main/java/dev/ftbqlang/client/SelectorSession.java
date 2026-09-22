package dev.ftbqlang.client;

/** Keeps a requested modal alive across FTB's screen rebuilds, until the player dismisses it. */
public final class SelectorSession {
    private boolean requested;
    private Object host;

    public void requestOpen() {
        requested = true;
    }

    public boolean shouldAttach(Object currentHost) {
        return requested && currentHost != null && host != currentHost;
    }

    public boolean isAttachedTo(Object currentHost) {
        return requested && host == currentHost;
    }

    public void attached(Object currentHost) {
        host = currentHost;
    }

    public void dismissed(Object closingHost) {
        if (host == closingHost) {
            reset();
        }
    }

    public void leftBook() {
        // A reply received after closing the book may still need its first presentation.
        if (host != null) {
            reset();
        }
    }

    public void reset() {
        requested = false;
        host = null;
    }
}
