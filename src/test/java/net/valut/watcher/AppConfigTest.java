package net.valut.watcher;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {
    @Test
    void usesExpectedDefaults() {
        AppConfig config = AppConfig.fromEnvironment(requiredEnvironment());

        assertEquals(5, config.sites().size());
        assertEquals(300, config.checkInterval().toSeconds());
        assertEquals(3, config.failureThreshold());
        assertEquals(10, config.connectTimeout().toSeconds());
        assertEquals(20, config.requestTimeout().toSeconds());
    }

    @Test
    void requiresTelegramSecrets() {
        Map<String, String> environment = new HashMap<>();
        environment.put("TELEGRAM_CHAT_ID", "-100123");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AppConfig.fromEnvironment(environment)
        );

        assertEquals("TELEGRAM_BOT_TOKEN is required", exception.getMessage());
    }

    @Test
    void rejectsNonHttpsSites() {
        Map<String, String> environment = requiredEnvironment();
        environment.put("MONITORED_URLS", "http://valut.net");

        assertThrows(IllegalArgumentException.class, () -> AppConfig.fromEnvironment(environment));
    }

    private static Map<String, String> requiredEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("TELEGRAM_BOT_TOKEN", "123:test");
        environment.put("TELEGRAM_CHAT_ID", "-100123");
        return environment;
    }
}
