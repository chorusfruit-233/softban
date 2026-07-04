package dev.softban.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SoftBanRepositoryTest {
    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    Path tempDir;

    @Test
    void createsIndependentVanillaShapedFiles() throws IOException {
        SoftBanRepository repository = repository();

        repository.load();
        repository.addPlayerBan(PLAYER_ID, "ExamplePlayer", "Console", "Testing player");
        repository.addIpBan("203.0.113.10", "Console", "Testing ip");

        String players = read("softbanned-players.json");
        String ips = read("softbanned-ips.json");

        assertTrue(players.contains("\"uuid\": \"" + PLAYER_ID + "\""));
        assertTrue(players.contains("\"name\": \"ExamplePlayer\""));
        assertTrue(players.contains("\"expires\": \"forever\""));
        assertTrue(players.contains("\"reason\": \"Testing player\""));
        assertTrue(ips.contains("\"ip\": \"203.0.113.10\""));
        assertTrue(ips.contains("\"reason\": \"Testing ip\""));
    }

    @Test
    void findsPlayerAndIpSoftBans() throws IOException {
        SoftBanRepository repository = repository();

        repository.load();
        repository.addPlayerBan(PLAYER_ID, "ExamplePlayer", "Console", "Testing player");
        repository.addIpBan("/203.0.113.10:25565", "Console", "Testing ip");

        assertTrue(repository.findPlayerBan(PLAYER_ID, "ExamplePlayer").isPresent());
        assertTrue(repository.findPlayerBan(UUID.randomUUID(), "exampleplayer").isPresent());
        assertTrue(repository.findIpBan("203.0.113.10").isPresent());
    }

    @Test
    void removesPlayerAndIpSoftBans() throws IOException {
        SoftBanRepository repository = repository();

        repository.load();
        repository.addPlayerBan(PLAYER_ID, "ExamplePlayer", "Console", "Testing player");
        repository.addIpBan("203.0.113.10", "Console", "Testing ip");

        assertTrue(repository.removePlayerBan(PLAYER_ID, "ExamplePlayer"));
        assertTrue(repository.removeIpBan("203.0.113.10"));
        assertFalse(repository.findPlayerBan(PLAYER_ID, "ExamplePlayer").isPresent());
        assertFalse(repository.findIpBan("203.0.113.10").isPresent());
    }

    @Test
    void purgesExpiredRecordsOnLoad() throws IOException {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("softbanned-players.json"), """
                [
                  {
                    "uuid": "11111111-2222-3333-4444-555555555555",
                    "name": "ExamplePlayer",
                    "created": "2024-01-01 00:00:00 +0000",
                    "source": "Console",
                    "expires": "2024-01-02 00:00:00 +0000",
                    "reason": "Expired"
                  }
                ]
                """, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("softbanned-ips.json"), "[]\n", StandardCharsets.UTF_8);

        SoftBanRepository repository = repository();
        repository.load();

        assertEquals(0, repository.listPlayerBans().size());
    }

    private SoftBanRepository repository() {
        return new SoftBanRepository(tempDir.toFile(), Logger.getLogger("SoftBanRepositoryTest"));
    }

    private String read(String fileName) throws IOException {
        return Files.readString(tempDir.resolve(fileName), StandardCharsets.UTF_8);
    }
}
