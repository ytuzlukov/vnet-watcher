package net.valut.watcher;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MonitoringService implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(MonitoringService.class.getName());

    private final AppConfig config;
    private final SiteChecker checker;
    private final AlertSender alertSender;
    private final Map<URI, SiteState> states;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService checkExecutor;

    public MonitoringService(AppConfig config, SiteChecker checker, AlertSender alertSender) {
        this.config = config;
        this.checker = checker;
        this.alertSender = alertSender;
        this.states = new LinkedHashMap<>();
        config.sites().forEach(site -> states.put(site, new SiteState()));
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("monitor-scheduler").factory()
        );
        this.checkExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(
                this::runSafely,
                0,
                config.checkInterval().toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    public List<SiteStatus> statusSnapshot() {
        List<SiteStatus> snapshot = new ArrayList<>();
        for (Map.Entry<URI, SiteState> entry : states.entrySet()) {
            SiteState state = entry.getValue();
            SiteStatus lastStatus = state.lastStatus;
            snapshot.add(lastStatus == null
                    ? new SiteStatus(entry.getKey(), null, null)
                    : lastStatus);
        }
        return List.copyOf(snapshot);
    }

    void runCheckCycle() {
        Map<URI, Future<CheckResult>> checks = new LinkedHashMap<>();
        config.sites().forEach(site -> checks.put(site, checkExecutor.submit(() -> checker.check(site))));

        for (Map.Entry<URI, Future<CheckResult>> check : checks.entrySet()) {
            CheckResult result = awaitResult(check.getKey(), check.getValue());
            if (result == null) {
                return;
            }
            processResult(check.getKey(), result);
        }
    }

    private void runSafely() {
        try {
            runCheckCycle();
        } catch (RuntimeException exception) {
            LOG.log(System.Logger.Level.ERROR, "Monitoring cycle failed unexpectedly", exception);
        }
    }

    private CheckResult awaitResult(URI site, Future<CheckResult> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.WARNING, "Monitoring cycle was interrupted");
            return null;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            String detail = cause == null
                    ? "внутренняя ошибка проверки"
                    : "внутренняя ошибка проверки: " + cause.getClass().getSimpleName();
            LOG.log(System.Logger.Level.ERROR, "Checker failed for " + site, cause);
            return CheckResult.unavailable(detail, 0);
        }
    }

    private void processResult(URI site, CheckResult result) {
        SiteState state = states.get(site);
        state.lastStatus = new SiteStatus(site, result, Instant.now());
        if (result.available()) {
            LOG.log(
                    System.Logger.Level.INFO,
                    "{0} is available ({1} ms)",
                    site,
                    result.elapsedMillis()
            );
            processSuccess(site, state);
            return;
        }

        state.consecutiveFailures++;
        state.lastFailureReason = result.detail();
        LOG.log(
                System.Logger.Level.WARNING,
                "{0} is unavailable: {1} ({2} ms), consecutive failure {3}",
                site,
                result.detail(),
                result.elapsedMillis(),
                state.consecutiveFailures
        );

        if (!state.outageAlertSent && state.consecutiveFailures >= config.failureThreshold()) {
            Alert alert = new Alert(
                    Alert.Type.OUTAGE,
                    site,
                    state.lastFailureReason,
                    estimatedOutageDuration(state)
            );
            if (sendAlert(alert)) {
                state.outageAlertSent = true;
            }
        }
    }

    private void processSuccess(URI site, SiteState state) {
        if (!state.outageAlertSent) {
            state.reset();
            return;
        }

        Alert recovery = new Alert(
                Alert.Type.RECOVERY,
                site,
                null,
                estimatedOutageDuration(state)
        );
        if (sendAlert(recovery)) {
            state.reset();
        }
    }

    private boolean sendAlert(Alert alert) {
        try {
            alertSender.send(alert);
            LOG.log(
                    System.Logger.Level.INFO,
                    "Sent {0} alert for {1}",
                    alert.type(),
                    alert.site()
            );
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.WARNING, "Sending Telegram alert was interrupted");
            return false;
        } catch (Exception exception) {
            LOG.log(
                    System.Logger.Level.ERROR,
                    "Could not send " + alert.type() + " alert for " + alert.site(),
                    exception
            );
            return false;
        }
    }

    private Duration estimatedOutageDuration(SiteState state) {
        return config.checkInterval().multipliedBy(state.consecutiveFailures);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        checkExecutor.shutdownNow();
    }

    private static final class SiteState {
        private int consecutiveFailures;
        private boolean outageAlertSent;
        private String lastFailureReason;
        private volatile SiteStatus lastStatus;

        private void reset() {
            consecutiveFailures = 0;
            outageAlertSent = false;
            lastFailureReason = null;
        }
    }
}
