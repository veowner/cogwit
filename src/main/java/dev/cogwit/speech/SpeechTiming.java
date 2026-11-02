package dev.cogwit.speech;

/** How long a speech bubble stays, how fast it types and how it fades. Times in milliseconds. Pure logic. */
public final class SpeechTiming {
    public static final long BASE_MILLIS = 4000, PER_CHAR_MILLIS = 55, HINT_MILLIS = 3000;
    public static final long MAX_MILLIS = 12000;
    public static final long FADE_MILLIS = 500;
    /** A question stays this long (the server gives up a little later). */
    public static final long ASK_MILLIS = 6500;
    /** Typing speed of the newest line. */
    public static final double CHARS_PER_MILLI = 0.06;
    /** Bubbles on screen at once; older ones are pushed out. */
    public static final int MAX_LINES = 3;

    private SpeechTiming() {}

    public static long lifetime(int chars, boolean hint) {
        if (hint) {
            return HINT_MILLIS;
        }
        return Math.min(MAX_MILLIS, BASE_MILLIS + chars * PER_CHAR_MILLIS);
    }

    /** How many characters are typed out {@code age} ms after the line appeared. */
    public static int visibleChars(long age, int chars, boolean hint) {
        if (hint) {
            return chars;
        }
        return (int) Math.min(chars, Math.max(0, Math.floor(age * CHARS_PER_MILLI)) + 1);
    }

    /** 1 while shown, fading to 0 over the last {@link #FADE_MILLIS}; 0 once gone. */
    public static float alpha(long age, long life) {
        if (age >= life) {
            return 0f;
        }
        long left = life - age;
        return left >= FADE_MILLIS ? 1f : (float) left / FADE_MILLIS;
    }

    /** A thought stays long enough to read at a glance: 2.5 s plus a little per letter, at most 6 s. */
    public static long thoughtLife(int length) {
        return Math.min(6000, 2500 + 45L * length);
    }
}
