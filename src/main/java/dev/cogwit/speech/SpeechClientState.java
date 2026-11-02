package dev.cogwit.speech;

import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** The lines Cogwit is saying on screen (newest first), his open question and the agent journal. */
public final class SpeechClientState {
    public enum Kind { SAY, HINT, ACTION, ASK }

    public record Line(Component text, Kind kind, long startMillis, long lifeMillis, int askId) {
        public boolean hint() {
            return kind == Kind.HINT;
        }
    }

    public record JournalEntry(Component text, long millis) {}

    /** The line above the hotbar: what Cogwit is thinking right now. */
    public record Thought(Component text, long startMillis, long lifeMillis) {}

    private static volatile Thought thought;

    private static final List<Line> LINES = new ArrayList<>();
    private static final Deque<JournalEntry> JOURNAL = new ArrayDeque<>();
    public static final int JOURNAL_SIZE = 16;

    private SpeechClientState() {}

    public static void add(Component text, boolean hint) {
        if (!hint && !SpeechFilter.shows(text, dev.cogwit.ClientConfig.chatter())) {
            return;
        }
        add(text, hint ? Kind.HINT : Kind.SAY, -1);
    }

    /** Something the agent just did: a short bubble and a journal line. */
    public static synchronized void action(Component text) {
        add(text, Kind.ACTION, -1);
        JOURNAL.addFirst(new JournalEntry(text, System.currentTimeMillis()));
        while (JOURNAL.size() > JOURNAL_SIZE) {
            JOURNAL.removeLast();
        }
    }

    public static void think(Component text) {
        thought = new Thought(text, System.currentTimeMillis(), SpeechTiming.thoughtLife(text.getString().length()));
    }

    /** The current thought, or null once it has faded. */
    public static Thought thought() {
        Thought t = thought;
        return t != null && System.currentTimeMillis() - t.startMillis() < t.lifeMillis() ? t : null;
    }

    public static void ask(int id, Component question) {
        add(question, Kind.ASK, id);
    }

    private static synchronized void add(Component text, Kind kind, int askId) {
        String plain = text.getString();
        boolean quick = kind == Kind.HINT || kind == Kind.ACTION;
        // the same hint twice in a row just refreshes the bubble; a new question replaces the old one
        LINES.removeIf(l -> (quick && l.kind() == kind && l.text().getString().equals(plain))
                || (kind == Kind.ASK && l.kind() == Kind.ASK));
        long life = kind == Kind.ASK ? SpeechTiming.ASK_MILLIS : SpeechTiming.lifetime(plain.length(), quick);
        LINES.add(0, new Line(text, kind, System.currentTimeMillis(), life, askId));
        while (LINES.size() > SpeechTiming.MAX_LINES) {
            LINES.remove(LINES.size() - 1);
        }
    }

    public static synchronized List<Line> lines() {
        long now = System.currentTimeMillis();
        LINES.removeIf(l -> now - l.startMillis() >= l.lifeMillis());
        return List.copyOf(LINES);
    }

    /** The question waiting for an answer, if any; answering removes it. */
    public static synchronized Line openQuestion() {
        for (Line l : lines()) {
            if (l.kind() == Kind.ASK) {
                return l;
            }
        }
        return null;
    }

    public static synchronized void answered(int askId) {
        LINES.removeIf(l -> l.kind() == Kind.ASK && l.askId() == askId);
    }

    public static synchronized List<JournalEntry> journal() {
        return List.copyOf(JOURNAL);
    }
}
