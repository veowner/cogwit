package dev.cogwit.compat;

import java.util.Locale;

/**
 * Controlify closes hid4java on exit, and on macOS hid_exit spins forever inside
 * IOHIDManagerClose, so the game never quits. The process is ending anyway, so on
 * macOS the HID cleanup is skipped (see ControlifyHidExitMixin).
 */
public final class ControlifyExitFix {
    private ControlifyExitFix() {}

    public static boolean appliesTo(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("mac");
    }

    public static boolean active() {
        return appliesTo(System.getProperty("os.name"));
    }

    /** Turns off hid4java's own shutdown hook, which would call the same hanging hid_exit. */
    public static void disableAutoShutdown(Object hidService) {
        try {
            var field = hidService.getClass().getDeclaredField("specification");
            field.setAccessible(true);
            Object spec = field.get(hidService);
            spec.getClass().getMethod("setAutoShutdown", boolean.class).invoke(spec, false);
        } catch (ReflectiveOperationException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("cogwit").warn("Controlify exit fix: could not disable HID shutdown hook", e);
        }
    }
}
