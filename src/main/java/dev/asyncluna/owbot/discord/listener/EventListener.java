package dev.asyncluna.owbot.discord.listener;

import discord4j.core.event.domain.Event;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public interface EventListener<T extends Event> {
  Logger log = LoggerFactory.getLogger(EventListener.class);

  Mono<Void> execute(T event);

  @SuppressWarnings("unchecked")
  default Class<T> getEventType() {
    try {
      for (Type type : getClass().getGenericInterfaces()) {
        if (type instanceof ParameterizedType parameterizedType) {
          if (parameterizedType.getRawType().equals(EventListener.class)) {
            return (Class<T>) parameterizedType.getActualTypeArguments()[0];
          }
        }
      }
    } catch (Exception exception) {
      log.error(
          "Failed to dynamically resolve event type for listener: {}",
          getClass().getName(),
          exception);
    }
    throw new IllegalStateException(
        "Could not resolve generic event type for " + getClass().getSimpleName());
  }

  default Mono<Void> handleException(Throwable exception) {
    log.error("Unhandled error processing event {}", getEventType().getSimpleName(), exception);
    return Mono.empty();
  }
}
