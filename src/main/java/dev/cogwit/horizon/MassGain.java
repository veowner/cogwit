package dev.cogwit.horizon;

/** New state after gaining mass, plus whatever didn't fit (later charges token cells). */
public record MassGain(HorizonState state, float overflow) {}
