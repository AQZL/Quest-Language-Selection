package dev.ftbqlang.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SelectorSessionTest {
    @Test
    void overlappingNetworkRepliesCannotDismissAnOpenSelector() {
        SelectorSession session = new SelectorSession();
        Object firstScreen = new Object();
        Object refreshedScreen = new Object();
        session.requestOpen();
        assertTrue(session.shouldAttach(firstScreen));
        session.attached(firstScreen);
        assertFalse(session.shouldAttach(firstScreen));
        // A later reply rebuilds FTB's screen without another manual open request.
        assertTrue(session.shouldAttach(refreshedScreen));
        session.attached(refreshedScreen);
        session.dismissed(firstScreen);
        assertFalse(session.shouldAttach(refreshedScreen));
        assertTrue(session.shouldAttach(new Object()));
    }

    @Test
    void explicitDismissalSurvivesLaterScreenRebuilds() {
        SelectorSession session = new SelectorSession();
        Object screen = new Object();
        session.requestOpen();
        session.attached(screen);
        session.dismissed(screen);
        assertFalse(session.shouldAttach(new Object()));
    }

    @Test
    void closingTheBookDismissesAnAlreadyPresentedModal() {
        SelectorSession session = new SelectorSession();
        session.requestOpen();
        session.attached(new Object());
        session.leftBook();
        assertFalse(session.shouldAttach(new Object()));
    }

    @Test
    void lateFirstOpenReplyWaitsForTheNextBookOpen() {
        SelectorSession session = new SelectorSession();
        session.requestOpen();
        session.leftBook();
        assertFalse(session.shouldAttach(null));
        assertTrue(session.shouldAttach(new Object()));
        session.reset();
        assertFalse(session.shouldAttach(new Object()));
    }
}
