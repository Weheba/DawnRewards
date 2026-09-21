package gg.dawn.rewards;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RewardJournalTest {
    @TempDir Path directory;
    @Test void preventsDuplicateGrantAcrossRestart() throws IOException {
        Path file = directory.resolve("rewards.log");
        RewardJournal first = new RewardJournal(file);
        assertTrue(first.reserve("claim-123"));
        assertFalse(first.reserve("claim-123"));
        RewardJournal restarted = new RewardJournal(file);
        assertFalse(restarted.reserve("claim-123"));
        assertTrue(restarted.reserve("claim-456"));
    }
    @Test void refusesMalformedClaimAndCorruptJournal() throws IOException {
        Path file = directory.resolve("rewards.log");
        RewardJournal journal = new RewardJournal(file);
        assertThrows(IOException.class, () -> journal.reserve("claim\nforged"));
        Files.writeString(file, "broken receipt!\n");
        assertThrows(IOException.class, () -> new RewardJournal(file));
    }
}
