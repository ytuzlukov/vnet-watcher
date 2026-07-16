package net.valut.watcher;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramStatusCommandPollerTest {

    @Test
    void parsesPrivateStatusCommand() {
        String response = """
                {"ok":true,"result":[{"update_id":42,"message":{"message_id":1,"chat":{"id":123456,"type":"private"},"text":"/status"}}]}
                """;

        List<TelegramStatusCommandPoller.TelegramUpdate> updates =
                TelegramStatusCommandPoller.TelegramUpdateParser.parse(response);

        assertEquals(1, updates.size());
        assertEquals(42, updates.getFirst().id());
        assertEquals("123456", updates.getFirst().chatId());
        assertEquals("private", updates.getFirst().chatType());
        assertTrue(updates.getFirst().isStatusCommand());
    }
}
