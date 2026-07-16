package net.valut.watcher;

import java.net.URI;
import java.time.Duration;

public record Alert(Type type, URI site, String reason, Duration estimatedDuration) {
    public Alert {
        if (type == null || site == null || estimatedDuration == null) {
            throw new IllegalArgumentException("Alert fields must not be null");
        }
        if (type == Type.OUTAGE && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Outage reason must not be blank");
        }
    }

    public enum Type {
        OUTAGE,
        RECOVERY
    }
}
