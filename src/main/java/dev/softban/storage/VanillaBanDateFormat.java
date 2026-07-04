package dev.softban.storage;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

final class VanillaBanDateFormat {
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("yyyy-MM-dd HH:mm:ss Z")
            .toFormatter(Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private VanillaBanDateFormat() {
    }

    static String format(Instant instant) {
        return FORMATTER.format(instant);
    }

    static Optional<Instant> parse(String value) {
        try {
            return Optional.of(Instant.from(FORMATTER.parse(value)));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }
}
