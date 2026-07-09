package dev.softban.command;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.softban.SoftBanPlugin;
import dev.softban.SoftBanService;
import dev.softban.storage.SoftBanRecord;
import dev.softban.storage.SoftBanRepository;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SoftBanCommand {
    private static final String DEFAULT_REASON = "Banned by an operator.";

    private final SoftBanPlugin plugin;
    private final SoftBanRepository repository;
    private final SoftBanService softBanService;

    public SoftBanCommand(SoftBanPlugin plugin, SoftBanRepository repository, SoftBanService softBanService) {
        this.plugin = plugin;
        this.repository = repository;
        this.softBanService = softBanService;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(softBanNode().build(), "Soft-ban a player.", List.of());
            commands.register(softBanIpNode().build(), "Soft-ban an IP address or an online player's current IP.", List.of());
            commands.register(softBanListNode().build(), "List active soft bans.", List.of());
            commands.register(softPardonNode().build(), "Remove a player soft ban.", List.of());
            commands.register(softPardonIpNode().build(), "Remove an IP soft ban.", List.of());
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> softBanNode() {
        return Commands.literal("softban")
                .requires(source -> hasPermission(source, "softban.command.softban"))
                .then(Commands.argument("targets", ArgumentTypes.playerProfiles())
                        .executes(context -> softBan(context, DEFAULT_REASON))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> softBan(context, StringArgumentType.getString(context, "reason")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> softBanIpNode() {
        return Commands.literal("softban-ip")
                .requires(source -> hasPermission(source, "softban.command.softbanip"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(this::suggestOnlinePlayers)
                        .executes(context -> softBanIp(context, DEFAULT_REASON))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> softBanIp(context, StringArgumentType.getString(context, "reason")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> softBanListNode() {
        return Commands.literal("softbanlist")
                .requires(source -> hasPermission(source, "softban.command.softbanlist"))
                .executes(context -> softBanList(context.getSource().getSender(), "players"))
                .then(Commands.literal("players")
                        .executes(context -> softBanList(context.getSource().getSender(), "players")))
                .then(Commands.literal("ips")
                        .executes(context -> softBanList(context.getSource().getSender(), "ips")));
    }

    private LiteralArgumentBuilder<CommandSourceStack> softPardonNode() {
        return Commands.literal("softpardon")
                .requires(source -> hasPermission(source, "softban.command.softpardon"))
                .then(Commands.argument("target", ArgumentTypes.playerProfiles())
                        .suggests(this::suggestSoftBannedPlayers)
                        .executes(this::softPardon));
    }

    private LiteralArgumentBuilder<CommandSourceStack> softPardonIpNode() {
        return Commands.literal("softpardon-ip")
                .requires(source -> hasPermission(source, "softban.command.softpardonip"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(this::suggestSoftBannedIps)
                        .executes(this::softPardonIp));
    }

    private int softBan(CommandContext<CommandSourceStack> context, String reason) {
        CommandSender sender = context.getSource().getSender();
        try {
            Collection<PlayerProfile> profiles = context.getArgument("targets", PlayerProfileListResolver.class).resolve(context.getSource());
            if (profiles.isEmpty()) {
                sender.sendMessage("No players matched.");
                return 0;
            }
            for (PlayerProfile profile : profiles) {
                UUID targetId = profile.getId();
                String targetName = profile.getName();
                if (targetId == null || targetName == null) {
                    sender.sendMessage("Could not resolve a complete profile for that player.");
                    continue;
                }

                SoftBanRecord record = repository.addPlayerBan(targetId, targetName, sender.getName(), reason);
                Player onlineTarget = Bukkit.getPlayer(targetId);
                if (onlineTarget != null) {
                    softBanService.markSoftBanned(onlineTarget, record);
                }
                sender.sendMessage("Soft-banned " + targetName + ".");
            }
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            reportFailure(sender, exception);
            return 0;
        }
    }

    private int softBanIp(CommandContext<CommandSourceStack> context, String reason) {
        CommandSender sender = context.getSource().getSender();
        String ip = resolveIp(StringArgumentType.getString(context, "target"));
        try {
            SoftBanRecord record = repository.addIpBan(ip, sender.getName(), reason);
            for (Player player : Bukkit.getOnlinePlayers()) {
                InetSocketAddress address = player.getAddress();
                if (address != null && SoftBanRecord.normalizeIp(address.getAddress().getHostAddress()).equals(record.ip)) {
                    softBanService.markSoftBanned(player, record);
                }
            }

            sender.sendMessage("Soft-banned IP address " + record.ip + ".");
            return Command.SINGLE_SUCCESS;
        } catch (IOException exception) {
            reportFailure(sender, exception);
            return 0;
        }
    }

    private int softBanList(CommandSender sender, String type) {
        try {
            List<SoftBanRecord> records = type.equals("players") ? repository.listPlayerBans() : repository.listIpBans();
            if (records.isEmpty()) {
                sender.sendMessage("There are no soft-banned " + type + ".");
                return Command.SINGLE_SUCCESS;
            }

            sender.sendMessage("There are " + records.size() + " soft-banned " + type + ":");
            sender.sendMessage(String.join(", ", records.stream().map(record -> type.equals("players") ? record.name : record.ip).toList()));
            return Command.SINGLE_SUCCESS;
        } catch (IOException exception) {
            reportFailure(sender, exception);
            return 0;
        }
    }

    private int softPardon(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        try {
            Collection<PlayerProfile> profiles = context.getArgument("target", PlayerProfileListResolver.class).resolve(context.getSource());
            if (profiles.isEmpty()) {
                sender.sendMessage("No players matched.");
                return 0;
            }

            boolean removedAny = false;
            for (PlayerProfile profile : profiles) {
                UUID targetId = profile.getId();
                String targetName = profile.getName();
                if (targetId == null || targetName == null) {
                    sender.sendMessage("Could not resolve a complete profile for that player.");
                    continue;
                }

                boolean removed = repository.removePlayerBan(targetId, targetName);
                Player onlineTarget = Bukkit.getPlayer(targetId);
                if (onlineTarget != null) {
                    softBanService.unmark(onlineTarget.getUniqueId());
                }
                sender.sendMessage(removed ? "Soft-pardoned " + targetName + "." : "Nothing changed. " + targetName + " is not soft-banned.");
                removedAny |= removed;
            }
            return removedAny ? Command.SINGLE_SUCCESS : 0;
        } catch (Exception exception) {
            reportFailure(sender, exception);
            return 0;
        }
    }

    private int softPardonIp(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String ip = resolveIp(StringArgumentType.getString(context, "target"));
        try {
            boolean removed = repository.removeIpBan(ip);
            for (Player player : Bukkit.getOnlinePlayers()) {
                InetSocketAddress address = player.getAddress();
                if (address != null && SoftBanRecord.normalizeIp(address.getAddress().getHostAddress()).equals(SoftBanRecord.normalizeIp(ip))) {
                    softBanService.unmark(player.getUniqueId());
                }
            }

            sender.sendMessage(removed ? "Soft-pardoned IP address " + SoftBanRecord.normalizeIp(ip) + "." : "Nothing changed. That IP is not soft-banned.");
            return removed ? Command.SINGLE_SUCCESS : 0;
        } catch (IOException exception) {
            reportFailure(sender, exception);
            return 0;
        }
    }

    private CompletableFuture<Suggestions> suggestOnlinePlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            builder.suggest(player.getName());
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestSoftBannedPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            for (SoftBanRecord record : repository.listPlayerBans()) {
                if (record.name != null && matchesRemaining(builder, record.name)) {
                    builder.suggest(record.name);
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to provide soft-pardon suggestions: " + exception.getMessage());
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestSoftBannedIps(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            for (SoftBanRecord record : repository.listIpBans()) {
                if (record.ip != null && matchesRemaining(builder, record.ip)) {
                    builder.suggest(record.ip);
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to provide soft-pardon-ip suggestions: " + exception.getMessage());
        }
        return builder.buildFuture();
    }

    private boolean hasPermission(CommandSourceStack source, String permission) {
        return source.getSender().hasPermission(permission);
    }

    private boolean matchesRemaining(SuggestionsBuilder builder, String candidate) {
        return candidate.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase());
    }

    private String resolveIp(String argument) {
        Player player = Bukkit.getPlayerExact(argument);
        if (player != null && player.getAddress() != null) {
            return player.getAddress().getAddress().getHostAddress();
        }
        return SoftBanRecord.normalizeIp(argument);
    }

    private void reportFailure(CommandSender sender, Exception exception) {
        sender.sendMessage("Failed to execute soft ban command. Check the server log for details.");
        plugin.getLogger().severe("Failed to execute soft ban command: " + exception.getMessage());
    }
}
