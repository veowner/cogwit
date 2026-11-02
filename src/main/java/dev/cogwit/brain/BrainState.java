package dev.cogwit.brain;

import java.util.List;
import java.util.Optional;

/**
 * Everything Cogwit remembers. Immutable: every change returns a new state.
 *
 * @param xp                Cogwit's own growth (see {@link Growth}), separate from what each skill has learned
 * @param sleptAt           when the current sleep started, so a Copper Apple knows whether it may wake him
 * @param rescuedAt         when Cogwit last saved his owner from death; starts the short invulnerability
 * @param backShieldReadyAt when the back shield can block again
 */
public record BrainState(List<Skill> skills, int tokens, long sleepUntil, long compactReadyAt,
                         long thinkReadyAt, long lastHurt, long lastRefusal, float xp, long sleptAt,
                         long rescuedAt, long backShieldReadyAt) {
    public static final BrainState EMPTY = new BrainState(List.of(), 0, 0, 0, 0, 0, 0);

    public BrainState {
        skills = List.copyOf(skills);
    }

    /** A Cogwit that has not grown yet. */
    public BrainState(List<Skill> skills, int tokens, long sleepUntil, long compactReadyAt, long thinkReadyAt,
            long lastHurt, long lastRefusal) {
        this(skills, tokens, sleepUntil, compactReadyAt, thinkReadyAt, lastHurt, lastRefusal, 0f, 0, 0, 0);
    }

    public Optional<Skill> find(String category) {
        return skills.stream().filter(s -> s.covers(category)).findFirst();
    }

    public BrainState withSkills(List<Skill> v) {
        return new BrainState(v, tokens, sleepUntil, compactReadyAt, thinkReadyAt, lastHurt, lastRefusal, xp,
                sleptAt, rescuedAt, backShieldReadyAt);
    }

    public BrainState withTokens(int v) {
        return new BrainState(skills, v, sleepUntil, compactReadyAt, thinkReadyAt, lastHurt, lastRefusal, xp,
                sleptAt, rescuedAt, backShieldReadyAt);
    }

    public BrainState withSleepUntil(long v) {
        return new BrainState(skills, tokens, v, compactReadyAt, thinkReadyAt, lastHurt, lastRefusal, xp,
                sleptAt, rescuedAt, backShieldReadyAt);
    }

    public BrainState withCompactReadyAt(long v) {
        return new BrainState(skills, tokens, sleepUntil, v, thinkReadyAt, lastHurt, lastRefusal, xp,
                sleptAt, rescuedAt, backShieldReadyAt);
    }

    public BrainState withThinkReadyAt(long v) {
        return new BrainState(skills, tokens, sleepUntil, compactReadyAt, v, lastHurt, lastRefusal, xp,
                sleptAt, rescuedAt, backShieldReadyAt);
    }

    public BrainState withLastHurt(long v) {
        return new BrainState(skills, tokens, sleepUntil, compactReadyAt, thinkReadyAt, v, lastRefusal, xp,
                sleptAt, rescuedAt, backShieldReadyAt);
    }

    public BrainState withLastRefusal(long v) {
        return new BrainState(skills, tokens, sleepUntil, compactReadyAt, thinkReadyAt, lastHurt, v, xp,
                sleptAt, rescuedAt, backShieldReadyAt);
    }

    public BrainState withXp(float v) {
        return new BrainState(skills, tokens, sleepUntil, compactReadyAt, thinkReadyAt, lastHurt, lastRefusal, v,
                sleptAt, rescuedAt, backShieldReadyAt);
    }

    /** Falls asleep now until {@code until}. */
    public BrainState asleep(long now, long until) {
        return new BrainState(skills, tokens, until, compactReadyAt, thinkReadyAt, lastHurt, lastRefusal, xp,
                now, rescuedAt, backShieldReadyAt);
    }

    public BrainState withRescuedAt(long v) {
        return new BrainState(skills, tokens, sleepUntil, compactReadyAt, thinkReadyAt, lastHurt, lastRefusal, xp,
                sleptAt, v, backShieldReadyAt);
    }

    public BrainState withBackShieldReadyAt(long v) {
        return new BrainState(skills, tokens, sleepUntil, compactReadyAt, thinkReadyAt, lastHurt, lastRefusal, xp,
                sleptAt, rescuedAt, v);
    }
}
