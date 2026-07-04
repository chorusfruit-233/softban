package dev.softban;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import dev.softban.command.SoftBanCommand;
import dev.softban.listener.LoginListener;
import dev.softban.packet.PacketSuppressor;
import dev.softban.storage.SoftBanRepository;
import java.io.IOException;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
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
        registerCommand("softban", command);
        registerCommand("softban-ip", command);
        registerCommand("softbanlist", command);
        registerCommand("softpardon", command);
        registerCommand("softpardon-ip", command);
    }

    @Override
    public void onDisable() {
        if (packetSuppressor != null) {
            packetSuppressor.unregister();
        }
    }

    private void registerCommand(String name, SoftBanCommand executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Command not defined in plugin.yml: " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
