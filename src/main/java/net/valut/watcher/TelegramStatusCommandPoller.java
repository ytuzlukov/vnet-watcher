package net.valut.watcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TelegramStatusCommandPoller implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(TelegramStatusCommandPoller.class.getName());

    private final TelegramAlertSender telegram;
    private final MonitoringService monitoringService;
    private final String allowedChatId;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean running;
    private long nextUpdateId;

    public TelegramStatusCommandPoller(
            TelegramAlertSender telegram,
            MonitoringService monitoringService,
            String allowedChatId
    ) {
        this.telegram = telegram;
        this.monitoringService = monitoringService;
        this.allowedChatId = allowedChatId;
    }

    public void start() {
        running = true;
        executor.submit(this::poll);
    }

    private void poll() {
        discardPendingUpdates();

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                List<TelegramUpdate> updates = TelegramUpdateParser.parse(telegram.getUpdates(nextUpdateId, 20));
                for (TelegramUpdate update : updates) {
                    nextUpdateId = Math.max(nextUpdateId, update.id() + 1);
                    if (isAllowedStatusCommand(update)) {
                        telegram.sendText(update.chatId(), StatusMessageFormatter.format(monitoringService.statusSnapshot()));
                        LOG.log(System.Logger.Level.INFO, "Sent /status response to Telegram chat {0}", update.chatId());
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException exception) {
                LOG.log(System.Logger.Level.WARNING, "Could not poll Telegram commands: {0}", exception.getMessage());
                sleepBeforeRetry();
            } catch (RuntimeException exception) {
                LOG.log(System.Logger.Level.ERROR, "Unexpected Telegram command polling failure", exception);
                sleepBeforeRetry();
            }
        }
    }

    private void discardPendingUpdates() {
        try {
            while (true) {
                List<TelegramUpdate> updates = TelegramUpdateParser.parse(telegram.getUpdates(nextUpdateId, 0));
                if (updates.isEmpty()) {
                    return;
                }
                for (TelegramUpdate update : updates) {
                    nextUpdateId = Math.max(nextUpdateId, update.id() + 1);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            LOG.log(System.Logger.Level.WARNING,
                    "Could not discard old Telegram updates; an old /status message may receive a response");
        }
    }

    private boolean isAllowedStatusCommand(TelegramUpdate update) {
        if (!update.isStatusCommand()) {
            return false;
        }
        if (allowedChatId != null) {
            return allowedChatId.equals(update.chatId());
        }
        return "private".equals(update.chatType());
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        running = false;
        executor.shutdownNow();
    }

    record TelegramUpdate(long id, String chatId, String chatType, String text) {
        boolean isStatusCommand() {
            return text != null && text.matches("/status(?:@[A-Za-z0-9_]+)?\\s*");
        }
    }

    static final class TelegramUpdateParser {
        private TelegramUpdateParser() {
        }

        static List<TelegramUpdate> parse(String response) {
            List<TelegramUpdate> updates = new ArrayList<>();
            String result = findArrayValue(response, "result");
            if (result == null) {
                return updates;
            }

            for (String updateJson : topLevelObjects(result)) {
                OptionalLong updateId = findLongValue(updateJson, "update_id");
                String message = findObjectValue(updateJson, "message");
                if (updateId.isEmpty() || message == null) {
                    continue;
                }

                String chat = findObjectValue(message, "chat");
                OptionalLong chatId = chat == null ? OptionalLong.empty() : findLongValue(chat, "id");
                String chatType = chat == null ? null : findStringValue(chat, "type");
                if (chatId.isEmpty() || chatType == null) {
                    continue;
                }

                updates.add(new TelegramUpdate(
                        updateId.getAsLong(),
                        Long.toString(chatId.getAsLong()),
                        chatType,
                        findStringValue(message, "text")
                ));
            }
            return updates;
        }

        private static String findArrayValue(String json, String name) {
            return findBalancedValue(json, name, '[', ']');
        }

        private static String findObjectValue(String json, String name) {
            return findBalancedValue(json, name, '{', '}');
        }

        private static String findBalancedValue(String json, String name, char open, char close) {
            int valueStart = findValueStart(json, name);
            if (valueStart < 0 || json.charAt(valueStart) != open) {
                return null;
            }
            int end = findBalancedEnd(json, valueStart, open, close);
            return end < 0 ? null : json.substring(valueStart, end + 1);
        }

        private static OptionalLong findLongValue(String json, String name) {
            int valueStart = findValueStart(json, name);
            if (valueStart < 0) {
                return OptionalLong.empty();
            }
            int end = valueStart;
            if (json.charAt(end) == '-') {
                end++;
            }
            while (end < json.length() && Character.isDigit(json.charAt(end))) {
                end++;
            }
            if (end == valueStart || (end == valueStart + 1 && json.charAt(valueStart) == '-')) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Long.parseLong(json.substring(valueStart, end)));
        }

        private static String findStringValue(String json, String name) {
            int valueStart = findValueStart(json, name);
            if (valueStart < 0 || json.charAt(valueStart) != '"') {
                return null;
            }
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            for (int index = valueStart + 1; index < json.length(); index++) {
                char current = json.charAt(index);
                if (escaped) {
                    value.append(switch (current) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> current;
                    });
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    return value.toString();
                } else {
                    value.append(current);
                }
            }
            return null;
        }

        private static int findValueStart(String json, String name) {
            int nameIndex = json.indexOf('"' + name + '"');
            if (nameIndex < 0) {
                return -1;
            }
            int colon = json.indexOf(':', nameIndex + name.length() + 2);
            if (colon < 0) {
                return -1;
            }
            int valueStart = colon + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }
            return valueStart;
        }

        private static int findBalancedEnd(String json, int start, char open, char close) {
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int index = start; index < json.length(); index++) {
                char current = json.charAt(index);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (current == '\\') {
                        escaped = true;
                    } else if (current == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (current == '"') {
                    inString = true;
                } else if (current == open) {
                    depth++;
                } else if (current == close && --depth == 0) {
                    return index;
                }
            }
            return -1;
        }

        private static List<String> topLevelObjects(String array) {
            List<String> objects = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                if (array.charAt(index) != '{') {
                    continue;
                }
                int end = findBalancedEnd(array, index, '{', '}');
                if (end < 0) {
                    break;
                }
                objects.add(array.substring(index, end + 1));
                index = end;
            }
            return objects;
        }
    }
}
