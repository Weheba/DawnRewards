package net.inpvp.dawnrewards.storage;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@NullMarked
public final class SqlResources {

    private SqlResources() {
    }

    public static String load(String resource) {
        var sql = find(resource);
        if (sql == null) {
            throw new IllegalStateException("Missing SQL resource " + resource);
        }
        return sql;
    }

    public static @Nullable String find(String resource) {
        try (var stream = SqlResources.class.getClassLoader().getResourceAsStream(resource)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read SQL resource " + resource, e);
        }
    }
}
