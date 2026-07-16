package net.valut.watcher;

import java.net.http.HttpClient;
import java.util.concurrent.CountDownLatch;

public final class Main {
    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            AppConfig config = AppConfig.fromEnvironment(System.getenv());
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.connectTimeout())
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            SiteChecker checker = new HttpSiteChecker(httpClient, config.requestTimeout());
            AlertSender alertSender = new TelegramAlertSender(
                    httpClient,
                    config.telegramBotToken(),
                    config.telegramChatId(),
                    config.requestTimeout()
            );
            MonitoringService monitoringService = new MonitoringService(config, checker, alertSender);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.log(System.Logger.Level.INFO, "Stopping vnet-watcher");
                monitoringService.close();
            }, "vnet-watcher-shutdown"));

            LOG.log(
                    System.Logger.Level.INFO,
                    "Starting vnet-watcher: {0} sites, interval {1} seconds, failure threshold {2}",
                    config.sites().size(),
                    config.checkInterval().toSeconds(),
                    config.failureThreshold()
            );
            monitoringService.start();
            new CountDownLatch(1).await();
        } catch (IllegalArgumentException exception) {
            LOG.log(System.Logger.Level.ERROR, "Configuration error: {0}", exception.getMessage());
            System.exit(1);
        }
    }
}
