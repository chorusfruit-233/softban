package dev.softban.listener;

import dev.softban.SoftBanService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class LoginListener implements Listener {
    private final SoftBanService softBanService;

    public LoginListener(SoftBanService softBanService) {
        this.softBanService = softBanService;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        softBanService.handlePreLogin(event);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        softBanService.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        softBanService.unmark(event.getPlayer().getUniqueId());
    }
}
