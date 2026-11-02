package dev.cogwit.agent;

import java.util.EnumMap;
import java.util.Map;

/** The agent settings kept on the talisman: behaviour mode and per-action permission overrides. */
public record AgentState(Agent.Mode mode, Map<Agent.Action, Agent.Permission> overrides) {
    public static final AgentState DEFAULT = new AgentState(Agent.Mode.CAREFUL, Map.of());

    public AgentState {
        overrides = overrides.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(overrides));
    }

    public Agent.Permission permission(Agent.Action action) {
        return Agent.permission(mode, overrides, action);
    }

    public AgentState withMode(Agent.Mode v) {
        return new AgentState(v, Map.of());
    }

    public AgentState with(Agent.Action action, Agent.Permission permission) {
        Map<Agent.Action, Agent.Permission> next = new EnumMap<>(Agent.Action.class);
        next.putAll(overrides);
        next.put(action, permission);
        return new AgentState(mode, next);
    }
}
