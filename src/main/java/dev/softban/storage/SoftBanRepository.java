package dev.softban.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class SoftBanRepository {
    private static final Type RECORD_LIST = new TypeToken<List<SoftBanRecord>>() {
    }.getType();

    private final Path playerBanFile;
    private final Path ipBanFile;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<UUID, SoftBanRecord> playerBans = new LinkedHashMap<>();
    private final Map<String, SoftBanRecord> ipBans = new LinkedHashMap<>();

    public SoftBanRepository(java.io.File dataFolder, Logger logger) {
        Path folder = dataFolder.toPath();
        this.playerBanFile = folder.resolve("softbanned-players.json");
        this.ipBanFile = folder.resolve("softbanned-ips.json");
        this.logger = logger;
    }

    public synchronized void load() throws IOException {
        Files.createDirectories(playerBanFile.getParent());
        playerBans.clear();
        ipBans.clear();

        for (SoftBanRecord record : readRecords(playerBanFile)) {
            if (record.uuid == null || record.uuid.isBlank()) {
                continue;
            }
            try {
                playerBans.put(UUID.fromString(record.uuid), record);
            } catch (IllegalArgumentException exception) {
                logger.warning("Skipping soft-banned player with invalid UUID: " + record.uuid);
            }
        }

        for (SoftBanRecord record : readRecords(ipBanFile)) {
            if (record.ip != null && !record.ip.isBlank()) {
                ipBans.put(SoftBanRecord.normalizeIp(record.ip), record);
            }
        }
        if (purgeExpired(false)) {
            saveAll();
        }
    }

    public synchronized Optional<SoftBanRecord> findPlayerBan(UUID uniqueId, String name) {
        SoftBanRecord byUuid = playerBans.get(uniqueId);
        if (byUuid != null && byUuid.matchesPlayer(uniqueId, name)) {
            return Optional.of(byUuid);
        }
        return playerBans.values().stream()
                .filter(record -> record.matchesPlayer(uniqueId, name))
                .findFirst();
    }

    public synchronized Optional<SoftBanRecord> findIpBan(String ip) {
        SoftBanRecord direct = ipBans.get(SoftBanRecord.normalizeIp(ip));
        if (direct != null && direct.matchesIp(ip)) {
            return Optional.of(direct);
        }
        return ipBans.values().stream()
                .filter(record -> record.matchesIp(ip))
                .findFirst();
    }

    public synchronized SoftBanRecord addPlayerBan(UUID uniqueId, String name, String source, String reason) throws IOException {
        SoftBanRecord record = SoftBanRecord.player(uniqueId, name, source, reason);
        playerBans.put(uniqueId, record);
        savePlayers();
        return record;
    }

    public synchronized SoftBanRecord addIpBan(String ip, String source, String reason) throws IOException {
        String normalizedIp = SoftBanRecord.normalizeIp(ip);
        SoftBanRecord record = SoftBanRecord.ip(normalizedIp, source, reason);
        ipBans.put(normalizedIp, record);
        saveIps();
        return record;
    }

    public synchronized boolean removePlayerBan(UUID uniqueId, String name) throws IOException {
        boolean removed = playerBans.remove(uniqueId) != null;
        removed |= playerBans.entrySet().removeIf(entry -> entry.getValue().matchesPlayer(uniqueId, name));
        if (removed) {
            savePlayers();
        }
        return removed;
    }

    public synchronized boolean removeIpBan(String ip) throws IOException {
        String normalizedIp = SoftBanRecord.normalizeIp(ip);
        boolean removed = ipBans.remove(normalizedIp) != null;
        removed |= ipBans.entrySet().removeIf(entry -> entry.getValue().matchesIp(normalizedIp));
        if (removed) {
            saveIps();
        }
        return removed;
    }

    public synchronized List<SoftBanRecord> listPlayerBans() throws IOException {
        if (purgeExpired(true)) {
            savePlayers();
        }
        return sorted(playerBans.values());
    }

    public synchronized List<SoftBanRecord> listIpBans() throws IOException {
        if (purgeExpired(true)) {
            saveIps();
        }
        return sorted(ipBans.values());
    }

    private List<SoftBanRecord> readRecords(Path path) throws IOException {
        if (!Files.exists(path)) {
            writeRecords(path, List.of());
            return List.of();
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            return List.of();
        }
        try {
            List<SoftBanRecord> records = gson.fromJson(json, RECORD_LIST);
            return records == null ? List.of() : records;
        } catch (JsonSyntaxException exception) {
            throw new IOException("Invalid JSON in " + path.getFileName(), exception);
        }
    }

    private boolean purgeExpired(boolean log) {
        int beforePlayers = playerBans.size();
        int beforeIps = ipBans.size();
        playerBans.entrySet().removeIf(entry -> entry.getValue().isExpired());
        ipBans.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = beforePlayers - playerBans.size() + beforeIps - ipBans.size();
        if (removed > 0 && log) {
            logger.info("Removed " + removed + " expired soft ban(s).");
        }
        return removed > 0;
    }

    private void saveAll() throws IOException {
        savePlayers();
        saveIps();
    }

    private void savePlayers() throws IOException {
        writeRecords(playerBanFile, sorted(playerBans.values()));
    }

    private void saveIps() throws IOException {
        writeRecords(ipBanFile, sorted(ipBans.values()));
    }

    private void writeRecords(Path path, List<SoftBanRecord> records) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, gson.toJson(records) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<SoftBanRecord> sorted(Iterable<SoftBanRecord> records) {
        List<SoftBanRecord> sorted = new ArrayList<>();
        records.forEach(sorted::add);
        sorted.sort(Comparator.comparing(record -> {
            if (record.name != null) {
                return record.name;
            }
            return record.ip == null ? "" : record.ip;
        }, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }
}
