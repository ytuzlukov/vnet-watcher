package net.valut.watcher;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class TelegramAlertSender implements AlertSender {
    private final URI sendMessageUri;
    private final String chatId;
    private final Duration requestTimeout;

    public TelegramAlertSender(
            String botToken,
            String chatId,
            Duration requestTimeout
    ) {
        if (!botToken.matches("[0-9]+:[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("TELEGRAM_BOT_TOKEN has invalid format");
        }
        this.sendMessageUri = URI.create("https://api.telegram.org/bot" + botToken + "/sendMessage");
        this.chatId = chatId;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public void send(Alert alert) throws IOException, InterruptedException {
        sendText(chatId, formatMessage(alert));
    }

    public void sendText(String destinationChatId, String text) throws IOException, InterruptedException {
        runCurl(List.of(
                "--data-urlencode", "chat_id=" + destinationChatId,
                "--data-urlencode", "text=" + text,
                "--data-urlencode", "disable_web_page_preview=true",
                sendMessageUri.toString()
        ), requestTimeout.toSeconds());
    }

    public String getUpdates(long offset, int timeoutSeconds) throws IOException, InterruptedException {
        String updatesUri = sendMessageUri.toString().replace("/sendMessage", "/getUpdates");
        return runCurl(List.of(
                "--get",
                "--data-urlencode", "offset=" + offset,
                "--data-urlencode", "timeout=" + timeoutSeconds,
                "--data-urlencode", "allowed_updates=[\"message\"]",
                updatesUri
        ), timeoutSeconds + 10L);
    }

    private String runCurl(List<String> requestArguments, long timeoutSeconds)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                "curl",
                "--fail",
                "--silent",
                "--show-error",
                "--ipv4",
                "--connect-timeout", Long.toString(Math.max(1, requestTimeout.toSeconds())),
                "--max-time", Long.toString(Math.max(1, timeoutSeconds))
        ));
        command.addAll(requestArguments);

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("curl Telegram request failed (exit " + exitCode + "): " + output.trim());
        }
        return output;
    }

    static String formatMessage(Alert alert) {
        String duration = formatDuration(alert.estimatedDuration());
        if (alert.type() == Alert.Type.OUTAGE) {
            return """
                    🔴 Сайт недоступен
                    Адрес: %s
                    Недоступен: %s
                    Причина: %s""".formatted(alert.site(), duration, alert.reason());
        }
        return """
                🟢 Сайт снова доступен
                Адрес: %s
                Всё работает нормально.
                Примерная продолжительность сбоя: %s""".formatted(alert.site(), duration);
    }

    static String formatDuration(Duration duration) {
        long totalSeconds = Math.max(1, duration.toSeconds());
        if (totalSeconds < 60) {
            return totalSeconds + " " + russianForm(totalSeconds, "секунда", "секунды", "секунд");
        }

        long totalMinutes = Math.max(1, duration.toMinutes());
        return totalMinutes + " " + russianForm(totalMinutes, "минута", "минуты", "минут");
    }

    private static String russianForm(long value, String one, String few, String many) {
        long mod100 = value % 100;
        if (mod100 >= 11 && mod100 <= 14) {
            return many;
        }
        return switch ((int) (value % 10)) {
            case 1 -> one;
            case 2, 3, 4 -> few;
            default -> many;
        };
    }

}
