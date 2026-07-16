package net.valut.watcher;

@FunctionalInterface
public interface AlertSender {
    void send(Alert alert) throws Exception;
}
