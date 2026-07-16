package net.valut.watcher;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitoringServiceTest {
    private static final URI SITE = URI.create("https://app.valut.net");

    @Test
    void transientFailureDoesNotSendMessages() {
        QueueChecker checker = new QueueChecker(
                failure("HTTP 502"),
                failure("таймаут ответа"),
                success()
        );
        RecordingAlertSender alerts = new RecordingAlertSender();

        try (MonitoringService service = service(checker, alerts)) {
            service.runCheckCycle();
            service.runCheckCycle();
            service.runCheckCycle();
        }

        assertEquals(List.of(), alerts.sent);
    }

    @Test
    void sendsOneOutageAndOneRecoveryAlert() {
        QueueChecker checker = new QueueChecker(
                failure("HTTP 502"),
                failure("HTTP 502"),
                failure("HTTP 502"),
                failure("HTTP 503"),
                success(),
                success()
        );
        RecordingAlertSender alerts = new RecordingAlertSender();

        try (MonitoringService service = service(checker, alerts)) {
            for (int i = 0; i < 6; i++) {
                service.runCheckCycle();
            }
        }

        assertEquals(2, alerts.sent.size());
        Alert outage = alerts.sent.get(0);
        assertEquals(Alert.Type.OUTAGE, outage.type());
        assertEquals("HTTP 502", outage.reason());
        assertEquals(Duration.ofMinutes(15), outage.estimatedDuration());

        Alert recovery = alerts.sent.get(1);
        assertEquals(Alert.Type.RECOVERY, recovery.type());
        assertEquals(Duration.ofMinutes(20), recovery.estimatedDuration());
    }

    @Test
    void retriesRecoveryMessageWhenTelegramTemporarilyFails() {
        QueueChecker checker = new QueueChecker(
                failure("HTTP 502"),
                failure("HTTP 502"),
                failure("HTTP 502"),
                success(),
                success()
        );
        RecordingAlertSender alerts = new RecordingAlertSender();
        alerts.failFirstRecovery = true;

        try (MonitoringService service = service(checker, alerts)) {
            for (int i = 0; i < 5; i++) {
                service.runCheckCycle();
            }
        }

        assertEquals(2, alerts.sent.size());
        assertEquals(Alert.Type.OUTAGE, alerts.sent.get(0).type());
        assertEquals(Alert.Type.RECOVERY, alerts.sent.get(1).type());
    }

    @Test
    void exposesLastCheckInStatusSnapshot() {
        QueueChecker checker = new QueueChecker(CheckResult.available(321));

        try (MonitoringService service = service(checker, new RecordingAlertSender())) {
            assertFalse(service.statusSnapshot().getFirst().checked());
            service.runCheckCycle();

            SiteStatus status = service.statusSnapshot().getFirst();
            assertTrue(status.checked());
            assertTrue(status.result().available());
            assertEquals(321, status.result().elapsedMillis());
        }
    }

    private static MonitoringService service(SiteChecker checker, AlertSender alerts) {
        AppConfig config = new AppConfig(
                List.of(SITE),
                "123:test",
                "-100123",
                null,
                Duration.ofMinutes(5),
                3,
                Duration.ofSeconds(10),
                Duration.ofSeconds(20)
        );
        return new MonitoringService(config, checker, alerts);
    }

    private static CheckResult success() {
        return CheckResult.available(10);
    }

    private static CheckResult failure(String reason) {
        return CheckResult.unavailable(reason, 10);
    }

    private static final class QueueChecker implements SiteChecker {
        private final Queue<CheckResult> results = new ArrayDeque<>();

        private QueueChecker(CheckResult... results) {
            this.results.addAll(List.of(results));
        }

        @Override
        public CheckResult check(URI site) {
            return results.remove();
        }
    }

    private static final class RecordingAlertSender implements AlertSender {
        private final List<Alert> sent = new ArrayList<>();
        private boolean failFirstRecovery;

        @Override
        public void send(Alert alert) {
            if (failFirstRecovery && alert.type() == Alert.Type.RECOVERY) {
                failFirstRecovery = false;
                throw new IllegalStateException("temporary Telegram error");
            }
            sent.add(alert);
        }
    }
}
