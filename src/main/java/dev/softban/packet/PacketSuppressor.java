package dev.softban.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import dev.softban.SoftBanService;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PacketSuppressor {
    private static final Set<String> ALLOWED_INITIAL_PACKETS = Set.of(
            "LOGIN",
            "RESPAWN",
            "PLAYER_POSITION",
            "POSITION",
            "GAME_STATE_CHANGE",
            "ABILITIES",
            "SERVER_DATA",
            "CUSTOM_PAYLOAD",
            "FEATURE_FLAGS",
            "UPDATE_ENABLED_FEATURES",
            "REGISTRY_DATA",
            "CONFIGURATION_START",
            "CONFIGURATION_END"
    );

    private final ProtocolManager protocolManager;
    private final PacketAdapter listener;

    public PacketSuppressor(Plugin plugin, SoftBanService softBanService, ProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
        this.listener = new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.getInstance().values()) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null || !softBanService.isSoftBanned(player)) {
                    return;
                }
                if (ALLOWED_INITIAL_PACKETS.contains(event.getPacketType().name())) {
                    return;
                }
                event.setCancelled(true);
            }
        };
    }

    public void register() {
        protocolManager.addPacketListener(listener);
    }

    public void unregister() {
        protocolManager.removePacketListener(listener);
    }
}
