package net.valut.watcher;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramAlertSenderTest {
    @Test
    void formatsOutageMessage() {
        Alert alert = new Alert(
                Alert.Type.OUTAGE,
                URI.create("https://app.valut.net"),
                "HTTP 502",
                Duration.ofMinutes(15)
        );

        String message = TelegramAlertSender.formatMessage(alert);

        assertTrue(message.contains("🔴 Сайт недоступен"));
        assertTrue(message.contains("https://app.valut.net"));
        assertTrue(message.contains("15 минут"));
        assertTrue(message.contains("HTTP 502"));
    }

    @Test
    void usesRussianDurationForms() {
        assertEquals("1 минута", TelegramAlertSender.formatDuration(Duration.ofMinutes(1)));
        assertEquals("2 минуты", TelegramAlertSender.formatDuration(Duration.ofMinutes(2)));
        assertEquals("5 минут", TelegramAlertSender.formatDuration(Duration.ofMinutes(5)));
        assertEquals("11 минут", TelegramAlertSender.formatDuration(Duration.ofMinutes(11)));
        assertEquals("21 минута", TelegramAlertSender.formatDuration(Duration.ofMinutes(21)));
    }

    @Test
    void formatsCurrentStatus() {
        SiteStatus status = new SiteStatus(
                URI.create("https://app.valut.net"),
                CheckResult.available(1234),
                java.time.Instant.parse("2026-07-17T10:15:30Z")
        );

        String message = StatusMessageFormatter.format(java.util.List.of(status));

        assertTrue(message.contains("app.valut.net"));
        assertTrue(message.contains("1234 мс"));
        assertTrue(message.contains("17.07.2026 10:15:30 UTC"));
    }
}
