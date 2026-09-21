package gg.dawn.rewards;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/** Records grants before dispatch. An ambiguous crash never automatically replays console commands. */
final class RewardJournal {
    private final Path file;
    private final Set<String> claims = new HashSet<>();

    RewardJournal(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.matches("[A-Za-z0-9_-]{1,128}")) throw new IOException("Invalid reward journal entry");
                claims.add(line);
            }
        }
    }

    synchronized boolean reserve(String claimId) throws IOException {
        if (!claimId.matches("[A-Za-z0-9_-]{1,128}")) throw new IOException("Invalid claim identifier");
        if (claims.contains(claimId)) return false;
        Files.createDirectories(file.getParent());
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
            ByteBuffer buffer = StandardCharsets.UTF_8.encode(claimId + "\n");
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        claims.add(claimId);
        return true;
    }
}
