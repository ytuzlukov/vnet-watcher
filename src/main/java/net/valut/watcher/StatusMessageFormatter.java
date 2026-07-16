package net.valut.watcher;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class StatusMessageFormatter {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    private StatusMessageFormatter() {
    }

    static String format(List<SiteStatus> statuses) {
        StringBuilder message = new StringBuilder("📊 Текущий статус сервисов\n");

        for (SiteStatus status : statuses) {
            message.append('\n').append(formatSite(status));
        }
        return message.toString();
    }

    private static String formatSite(SiteStatus status) {
        String host = status.site().getHost();
        if (!status.checked()) {
            return "⚪ " + host + " — ещё не проверялся";
        }

        CheckResult result = status.result();
        String timestamp = TIMESTAMP_FORMAT.format(status.checkedAt());
        if (result.available()) {
            return "🟢 " + host + " — OK, " + result.elapsedMillis()
                    + " мс\n   Последняя проверка: " + timestamp;
        }
        return "🔴 " + host + " — " + result.detail()
                + "\n   Последняя проверка: " + timestamp;
    }
}
