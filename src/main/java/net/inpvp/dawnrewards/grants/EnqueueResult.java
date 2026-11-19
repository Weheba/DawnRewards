package net.inpvp.dawnrewards.grants;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record EnqueueResult(CosmeticGrant grant, Outcome outcome) {

    public enum Outcome {
        /** New purchase, queued. */
        QUEUED,
        /** The same delivery is already waiting in the queue. */
        ALREADY_QUEUED,
        /** The same delivery was already granted. */
        ALREADY_GRANTED,
        /** The same delivery had failed or expired and is queued again. */
        REQUEUED
    }
}
