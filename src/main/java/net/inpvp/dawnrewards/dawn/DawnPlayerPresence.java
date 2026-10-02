package net.inpvp.dawnrewards.dawn;

import lombok.Getter;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;

@NullMarked
@Getter
public class DawnPlayerPresence {

    public static final DawnPlayerPresence UNKNOWN = new DawnPlayerPresence(false, false);

    private final boolean cookieValidated;
    private final boolean presenceValidated;
    private final Instant recordedAt = Instant.now();

    public DawnPlayerPresence(boolean cookieValidated, boolean presenceValidated) {
        this.cookieValidated = cookieValidated;
        this.presenceValidated = presenceValidated;
    }

    public boolean isDawnConnected() {
        return cookieValidated && presenceValidated;
    }
}
