package dev.softban;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import dev.softban.command.SoftBanCommand;
import dev.softban.listener.LoginListener;
import dev.softban.packet.PacketSuppressor;
import dev.softban.storage.SoftBanRepository;
import java.io.IOException;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoftBanPlugin extends JavaPlugin {
    private SoftBanRepository repository;
    private SoftBanService softBanService;
    private PacketSuppressor packetSuppressor;

    @Override
    public void onEnable() {
        repository = new SoftBanRepository(getDataFolder(), getLogger());
        try {
            repository.load();
        } catch (IOException exception) {
            getLogger().severe("Failed to load soft ban files: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        softBanService = new SoftBanService(repository);
        getServer().getPluginManager().registerEvents(new LoginListener(softBanService), this);

        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        packetSuppressor = new PacketSuppressor(this, softBanService, protocolManager);
        packetSuppressor.register();

        SoftBanCommand command = new SoftBanCommand(this, repository, softBanService);
        command.register();
    }

    @Override
    public void onDisable() {
        if (packetSuppressor != null) {
            packetSuppressor.unregister();
        }
    }
}
