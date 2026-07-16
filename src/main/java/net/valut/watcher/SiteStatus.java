package net.valut.watcher;

import java.net.URI;
import java.time.Instant;

public record SiteStatus(URI site, CheckResult result, Instant checkedAt) {
    public boolean checked() {
        return result != null && checkedAt != null;
    }
}
