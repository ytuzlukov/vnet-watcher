package net.valut.watcher;

import java.net.URI;

@FunctionalInterface
public interface SiteChecker {
    CheckResult check(URI site);
}
