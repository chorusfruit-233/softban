package dev.softban;

import dev.softban.storage.SoftBanRecord;
import dev.softban.storage.SoftBanRepository;
import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public final class SoftBanService {
    private final SoftBanRepository repository;
    private final ConcurrentMap<UUID, SoftBanRecord> activeSoftBans = new ConcurrentHashMap<>();

    public SoftBanService(SoftBanRepository repository) {
        this.repository = repository;
    }

    public void handlePreLogin(AsyncPlayerPreLoginEvent event) {
        findBan(event.getUniqueId(), event.getName(), event.getAddress())
                .ifPresent(record -> activeSoftBans.put(event.getUniqueId(), record));
    }

    public void handleJoin(Player player) {
        findBan(player.getUniqueId(), player.getName(), player.getAddress().getAddress())
                .ifPresent(record -> activeSoftBans.put(player.getUniqueId(), record));
    }

    public boolean isSoftBanned(Player player) {
        return activeSoftBans.containsKey(player.getUniqueId());
    }

    public void markSoftBanned(Player player, SoftBanRecord record) {
        activeSoftBans.put(player.getUniqueId(), record);
    }

    public void unmark(UUID uniqueId) {
        activeSoftBans.remove(uniqueId);
    }

    private Optional<SoftBanRecord> findBan(UUID uniqueId, String name, InetAddress address) {
        Optional<SoftBanRecord> playerBan = repository.findPlayerBan(uniqueId, name);
        if (playerBan.isPresent()) {
            return playerBan;
        }
        return repository.findIpBan(address.getHostAddress());
    }
}
