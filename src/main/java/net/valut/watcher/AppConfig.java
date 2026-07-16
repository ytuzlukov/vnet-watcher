package net.valut.watcher;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record AppConfig(
        List<URI> sites,
        String telegramBotToken,
        String telegramChatId,
        Duration checkInterval,
        int failureThreshold,
        Duration connectTimeout,
        Duration requestTimeout
) {
    private static final String DEFAULT_SITES = String.join(",",
            "https://valut.net",
            "https://app.valut.net",
            "https://pro.valut.net",
            "https://docs.valut.net",
            "https://app.travel.cards"
    );

    public AppConfig {
        sites = List.copyOf(sites);
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("At least one monitored site is required");
        }
        if (telegramBotToken == null || telegramBotToken.isBlank()) {
            throw new IllegalArgumentException("TELEGRAM_BOT_TOKEN is required");
        }
        if (telegramChatId == null || telegramChatId.isBlank()) {
            throw new IllegalArgumentException("TELEGRAM_CHAT_ID is required");
        }
        requirePositive(checkInterval, "CHECK_INTERVAL_SECONDS");
        requirePositive(connectTimeout, "CONNECT_TIMEOUT_SECONDS");
        requirePositive(requestTimeout, "REQUEST_TIMEOUT_SECONDS");
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("FAILURE_THRESHOLD must be greater than zero");
        }
    }

    public static AppConfig fromEnvironment(Map<String, String> environment) {
        return new AppConfig(
                parseSites(environment.getOrDefault("MONITORED_URLS", DEFAULT_SITES)),
                required(environment, "TELEGRAM_BOT_TOKEN"),
                required(environment, "TELEGRAM_CHAT_ID"),
                Duration.ofSeconds(positiveLong(environment, "CHECK_INTERVAL_SECONDS", 300)),
                positiveInteger(environment, "FAILURE_THRESHOLD", 3),
                Duration.ofSeconds(positiveLong(environment, "CONNECT_TIMEOUT_SECONDS", 10)),
                Duration.ofSeconds(positiveLong(environment, "REQUEST_TIMEOUT_SECONDS", 20))
        );
    }

    private static List<URI> parseSites(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MONITORED_URLS must not be empty");
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(site -> !site.isEmpty())
                .map(AppConfig::parseSite)
                .toList();
    }

    private static URI parseSite(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid URL in MONITORED_URLS: " + value, exception);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Only absolute HTTPS URLs are allowed: " + value);
        }
        return uri;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static int positiveInteger(Map<String, String> environment, String name, int defaultValue) {
        long value = positiveLong(environment, name, defaultValue);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " is too large");
        }
        return (int) value;
    }

    private static long positiveLong(Map<String, String> environment, String name, long defaultValue) {
        String rawValue = environment.get(name);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        try {
            long value = Long.parseLong(rawValue.trim());
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be greater than zero");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive integer", exception);
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }
}
