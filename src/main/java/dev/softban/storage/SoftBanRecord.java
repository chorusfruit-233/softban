package dev.softban.storage;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class SoftBanRecord {
    private static final String FOREVER = "forever";

    public String uuid;
    public String name;
    public String ip;
    public String created;
    public String source;
    public String expires;
    public String reason;

    public static SoftBanRecord player(UUID uniqueId, String name, String source, String reason) {
        SoftBanRecord record = base(source, reason);
        record.uuid = uniqueId.toString();
        record.name = name;
        return record;
    }

    public static SoftBanRecord ip(String ip, String source, String reason) {
        SoftBanRecord record = base(source, reason);
        record.ip = ip;
        return record;
    }

    private static SoftBanRecord base(String source, String reason) {
        SoftBanRecord record = new SoftBanRecord();
        record.created = VanillaBanDateFormat.format(Instant.now());
        record.source = source;
        record.expires = FOREVER;
        record.reason = reason;
        return record;
    }

    public boolean isExpired() {
        if (expires == null || FOREVER.equalsIgnoreCase(expires)) {
            return false;
        }
        return VanillaBanDateFormat.parse(expires)
                .map(expiry -> !expiry.isAfter(Instant.now()))
                .orElse(false);
    }

    public UUID playerUuid() {
        return uuid == null ? null : UUID.fromString(uuid);
    }

    public boolean matchesPlayer(UUID uniqueId, String playerName) {
        if (isExpired()) {
            return false;
        }
        if (uuid != null && Objects.equals(playerUuid(), uniqueId)) {
            return true;
        }
        return name != null && playerName != null && name.equalsIgnoreCase(playerName);
    }

    public boolean matchesIp(String address) {
        return !isExpired() && ip != null && normalizeIp(ip).equals(normalizeIp(address));
    }

    public static String normalizeIp(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int slash = normalized.indexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(0, slash);
        }
        int colon = normalized.indexOf(':');
        if (colon > 0 && normalized.indexOf(':', colon + 1) < 0 && normalized.substring(colon + 1).matches("\\d+")) {
            normalized = normalized.substring(0, colon);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
