package dev.softban.command;

import dev.softban.SoftBanService;
import dev.softban.storage.SoftBanRecord;
import dev.softban.storage.SoftBanRepository;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class SoftBanCommand implements CommandExecutor, TabCompleter {
    private static final String DEFAULT_REASON = "Banned by an operator.";

    private final Plugin plugin;
    private final SoftBanRepository repository;
    private final SoftBanService softBanService;

    public SoftBanCommand(Plugin plugin, SoftBanRepository repository, SoftBanService softBanService) {
        this.plugin = plugin;
        this.repository = repository;
        this.softBanService = softBanService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "softban" -> softBan(sender, args);
                case "softban-ip" -> softBanIp(sender, args);
                case "softbanlist" -> softBanList(sender, args);
                case "softpardon" -> softPardon(sender, args);
                case "softpardon-ip" -> softPardonIp(sender, args);
                default -> false;
            };
        } catch (IOException exception) {
            sender.sendMessage("Failed to update soft ban files. Check the server log for details.");
            plugin.getLogger().severe("Failed to update soft ban files: " + exception.getMessage());
            return true;
        }
    }

    private boolean softBan(CommandSender sender, String[] args) throws IOException {
        if (args.length < 1) {
            return false;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID targetId = target.getUniqueId();
        String targetName = target.getName() == null ? args[0] : target.getName();
        SoftBanRecord record = repository.addPlayerBan(targetId, targetName, sender.getName(), reason(args, 1));

        Player onlineTarget = Bukkit.getPlayer(targetId);
        if (onlineTarget != null) {
            softBanService.markSoftBanned(onlineTarget, record);
        }

        sender.sendMessage("Soft-banned " + targetName + ".");
        return true;
    }

    private boolean softBanIp(CommandSender sender, String[] args) throws IOException {
        if (args.length < 1) {
            return false;
        }

        String ip = resolveIp(args[0]);
        SoftBanRecord record = repository.addIpBan(ip, sender.getName(), reason(args, 1));
        for (Player player : Bukkit.getOnlinePlayers()) {
            InetSocketAddress address = player.getAddress();
            if (address != null && SoftBanRecord.normalizeIp(address.getAddress().getHostAddress()).equals(record.ip)) {
                softBanService.markSoftBanned(player, record);
            }
        }

        sender.sendMessage("Soft-banned IP address " + record.ip + ".");
        return true;
    }

    private boolean softBanList(CommandSender sender, String[] args) throws IOException {
        String type = args.length == 0 ? "players" : args[0].toLowerCase(Locale.ROOT);
        if (!type.equals("players") && !type.equals("ips")) {
            return false;
        }

        List<SoftBanRecord> records = type.equals("players") ? repository.listPlayerBans() : repository.listIpBans();
        if (records.isEmpty()) {
            sender.sendMessage("There are no soft-banned " + type + ".");
            return true;
        }

        sender.sendMessage("There are " + records.size() + " soft-banned " + type + ":");
        sender.sendMessage(String.join(", ", records.stream().map(record -> type.equals("players") ? record.name : record.ip).toList()));
        return true;
    }

    private boolean softPardon(CommandSender sender, String[] args) throws IOException {
        if (args.length != 1) {
            return false;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        boolean removed = repository.removePlayerBan(target.getUniqueId(), args[0]);
        Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
        if (onlineTarget != null) {
            softBanService.unmark(onlineTarget.getUniqueId());
        }

        sender.sendMessage(removed ? "Soft-pardoned " + args[0] + "." : "Nothing changed. " + args[0] + " is not soft-banned.");
        return true;
    }

    private boolean softPardonIp(CommandSender sender, String[] args) throws IOException {
        if (args.length != 1) {
            return false;
        }

        String ip = resolveIp(args[0]);
        boolean removed = repository.removeIpBan(ip);
        for (Player player : Bukkit.getOnlinePlayers()) {
            InetSocketAddress address = player.getAddress();
            if (address != null && SoftBanRecord.normalizeIp(address.getAddress().getHostAddress()).equals(SoftBanRecord.normalizeIp(ip))) {
                softBanService.unmark(player.getUniqueId());
            }
        }

        sender.sendMessage(removed ? "Soft-pardoned IP address " + SoftBanRecord.normalizeIp(ip) + "." : "Nothing changed. That IP is not soft-banned.");
        return true;
    }

    private String resolveIp(String argument) {
        Player player = Bukkit.getPlayerExact(argument);
        if (player != null && player.getAddress() != null) {
            return player.getAddress().getAddress().getHostAddress();
        }
        return SoftBanRecord.normalizeIp(argument);
    }

    private String reason(String[] args, int start) {
        if (args.length <= start) {
            return DEFAULT_REASON;
        }
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("softbanlist") && args.length == 1) {
            return filter(List.of("players", "ips"), args[0]);
        }
        if ((name.equals("softban") || name.equals("softban-ip") || name.equals("softpardon") || name.equals("softpardon-ip")) && args.length == 1) {
            List<String> candidates = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                candidates.add(player.getName());
            }
            return filter(candidates, args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> candidates, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
