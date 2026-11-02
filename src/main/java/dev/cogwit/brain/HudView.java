package dev.cogwit.brain;

import java.util.List;

/** Everything the on-screen panel shows, computed from Cogwit's memory without touching rendering. */
public record HudView(Mood mood, long moodTicksLeft, int tokens, List<Row> rows, long thinkTicksLeft,
                      long compactTicksLeft, boolean regenActive, boolean shieldReady, int level,
                      float levelProgress, long wakeTicksLeft, boolean backShieldUnlocked,
                      long backShieldTicksLeft) {
    /** A skill counts as "just used" for this long, so the panel can flash it. */
    public static final long RECENT_TICKS = 40;

    public enum Mood { AWAKE, THINKING, ASLEEP }

    public record Row(String category, int percent, float levelProgress, boolean compacted, String merged,
                      int mergedPercent, boolean recentlyUsed) {}

    public static HudView of(BrainState s, long now) {
        boolean asleep = Brain.isAsleep(s, now);
        Mood mood;
        long moodLeft = 0;
        if (asleep) {
            mood = Mood.ASLEEP;
            moodLeft = s.sleepUntil() - now;
        } else if (Brain.isThinking(s, now)) {
            mood = Mood.THINKING;
            moodLeft = s.thinkReadyAt() - Brain.THINK_COOLDOWN_TICKS + Brain.THINK_DURATION_TICKS - now;
        } else {
            mood = Mood.AWAKE;
        }

        List<Row> rows = s.skills().stream().map(skill -> new Row(
                skill.category(),
                Math.round(skill.reductionFor(skill.category()) * 100),
                skill.compacted() ? 1f : Brain.levelProgress(skill.xp()),
                skill.compacted(),
                skill.merged(),
                skill.compacted() ? Math.round(skill.reductionFor(skill.merged()) * 100) : 0,
                now - skill.lastUsed() < RECENT_TICKS)).toList();

        boolean regen = !asleep && now - s.lastHurt() >= Brain.REGEN_DELAY_TICKS
                && s.tokens() >= Brain.REGEN_TOKEN_COST;
        boolean shield = !asleep && s.tokens() >= Brain.EFFECT_TOKEN_COST;
        return new HudView(mood, moodLeft, s.tokens(), rows, Math.max(0, s.thinkReadyAt() - now),
                Math.max(0, s.compactReadyAt() - now), regen, shield, Growth.level(s.xp()), Growth.progress(s.xp()),
                asleep ? Brain.wakeTicksLeft(s, now) : 0, Growth.has(s.xp(), Growth.Perk.BACK_SHIELD),
                Math.max(0, s.backShieldReadyAt() - now));
    }
}
