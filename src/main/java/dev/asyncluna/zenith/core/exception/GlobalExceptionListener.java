package dev.asyncluna.zenith.core.exception;

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Hooks;

@Component
@Slf4j
public class GlobalExceptionListener {
    @PostConstruct
    public void init() {
        Hooks.onErrorDropped(Sentry::captureException);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> Sentry.captureException(throwable));
    }

    @PreDestroy
    public void cleanup() {
        Hooks.resetOnErrorDropped();
    }
}
