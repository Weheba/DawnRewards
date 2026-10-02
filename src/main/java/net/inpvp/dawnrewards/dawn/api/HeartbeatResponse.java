package net.inpvp.dawnrewards.dawn.api;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record HeartbeatResponse(boolean accepted, boolean accountingEnabled) {
}
