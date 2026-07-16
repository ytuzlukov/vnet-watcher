package net.valut.watcher;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class TelegramAlertSender implements AlertSender {
    private final HttpClient httpClient;
    private final URI sendMessageUri;
    private final String chatId;
    private final Duration requestTimeout;

    public TelegramAlertSender(
            HttpClient httpClient,
            String botToken,
            String chatId,
            Duration requestTimeout
    ) {
        if (!botToken.matches("[0-9]+:[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("TELEGRAM_BOT_TOKEN has invalid format");
        }
        this.httpClient = httpClient;
        this.sendMessageUri = URI.create("https://api.telegram.org/bot" + botToken + "/sendMessage");
        this.chatId = chatId;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public void send(Alert alert) throws IOException, InterruptedException {
        sendText(chatId, formatMessage(alert));
    }

    public void sendText(String destinationChatId, String text) throws IOException, InterruptedException {
        String requestBody = "chat_id=" + encode(destinationChatId)
                + "&text=" + encode(text)
                + "&disable_web_page_preview=true";

        HttpRequest request = HttpRequest.newBuilder(sendMessageUri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Telegram API returned HTTP " + response.statusCode());
        }
    }

    public String getUpdates(long offset, int timeoutSeconds) throws IOException, InterruptedException {
        URI updatesUri = URI.create(sendMessageUri.toString().replace("/sendMessage", "/getUpdates")
                + "?offset=" + offset
                + "&timeout=" + timeoutSeconds
                + "&allowed_updates=%5B%22message%22%5D");
        HttpRequest request = HttpRequest.newBuilder(updatesUri)
                .timeout(Duration.ofSeconds(timeoutSeconds + 10L))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Telegram getUpdates returned HTTP " + response.statusCode());
        }
        return response.body();
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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
