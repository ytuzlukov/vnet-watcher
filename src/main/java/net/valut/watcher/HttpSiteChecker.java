package net.valut.watcher;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class HttpSiteChecker implements SiteChecker {
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HttpSiteChecker(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public CheckResult check(URI site) {
        long startedAt = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(site)
                .GET()
                .timeout(requestTimeout)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "vnet-watcher/1.0")
                .build();

        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long elapsedMillis = elapsedMillisSince(startedAt);
            if (response.statusCode() == 200) {
                return CheckResult.available(elapsedMillis);
            }
            return CheckResult.unavailable("HTTP " + response.statusCode(), elapsedMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CheckResult.unavailable("проверка была прервана", elapsedMillisSince(startedAt));
        } catch (IOException exception) {
            return CheckResult.unavailable(describeNetworkError(exception), elapsedMillisSince(startedAt));
        } catch (RuntimeException exception) {
            return CheckResult.unavailable(
                    "неожиданная ошибка: " + safeMessage(exception),
                    elapsedMillisSince(startedAt)
            );
        }
    }

    private String describeNetworkError(IOException exception) {
        if (hasCause(exception, HttpTimeoutException.class)) {
            return "таймаут ответа (" + requestTimeout.toSeconds() + " с)";
        }
        if (hasCause(exception, UnknownHostException.class)) {
            return "ошибка DNS: " + safeMessage(exception);
        }
        if (hasCause(exception, SSLException.class)) {
            return "ошибка TLS: " + safeMessage(exception);
        }
        if (hasCause(exception, ConnectException.class)) {
            return "не удалось подключиться: " + safeMessage(exception);
        }
        return "ошибка сети: " + safeMessage(exception);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() <= 300 ? singleLine : singleLine.substring(0, 300);
    }

    private static long elapsedMillisSince(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
