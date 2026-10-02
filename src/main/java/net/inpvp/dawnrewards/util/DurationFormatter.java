package net.inpvp.dawnrewards.util;

import org.jspecify.annotations.NullMarked;

import java.time.Duration;

@NullMarked
public final class DurationFormatter {

    private DurationFormatter() {
    }

    public static String format(Duration duration) {
        var seconds = Math.max(0, duration.getSeconds());
        var builder = new StringBuilder();

        var days = seconds / 86400;
        var hours = seconds % 86400 / 3600;
        var minutes = seconds % 3600 / 60;

        if (days > 0) {
            builder.append(days).append('d');
        }
        if (hours > 0) {
            builder.append(hours).append('h');
        }
        if (minutes > 0) {
            builder.append(minutes).append('m');
        }

        return builder.append(seconds % 60).append('s').toString();
    }
}
