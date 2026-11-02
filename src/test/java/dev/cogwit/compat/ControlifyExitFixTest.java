package dev.cogwit.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlifyExitFixTest {
    @Test
    void skipsHidShutdownOnlyOnMac() {
        assertTrue(ControlifyExitFix.appliesTo("Mac OS X"));
        assertTrue(ControlifyExitFix.appliesTo("macOS"));
        assertFalse(ControlifyExitFix.appliesTo("Windows 11"));
        assertFalse(ControlifyExitFix.appliesTo("Linux"));
        assertFalse(ControlifyExitFix.appliesTo(null));
    }
}
