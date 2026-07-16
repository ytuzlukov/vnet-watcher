package net.valut.watcher;

public record CheckResult(boolean available, String detail, long elapsedMillis) {
    public CheckResult {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("Check result detail must not be blank");
        }
        if (elapsedMillis < 0) {
            throw new IllegalArgumentException("Elapsed time must not be negative");
        }
    }

    public static CheckResult available(long elapsedMillis) {
        return new CheckResult(true, "HTTP 200", elapsedMillis);
    }

    public static CheckResult unavailable(String detail, long elapsedMillis) {
        return new CheckResult(false, detail, elapsedMillis);
    }
}
