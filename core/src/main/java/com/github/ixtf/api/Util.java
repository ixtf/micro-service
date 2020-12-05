package com.github.ixtf.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.ixtf.japp.core.J;
import com.sun.security.auth.UserPrincipal;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.TextMapAdapter;
import io.vertx.core.eventbus.Message;
import lombok.SneakyThrows;

import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import java.io.IOException;
import java.security.Principal;
import java.util.Optional;

import static com.github.ixtf.japp.core.Constant.MAPPER;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP;
import static java.util.Optional.ofNullable;

public class Util {

    @SneakyThrows(IOException.class)
    public static <T> T checkAndGetCommand(Class<T> clazz, byte[] bytes) {
        final T command = MAPPER.readValue(bytes, clazz);
        return checkAndGetCommand(command);
    }

    @SneakyThrows(JsonProcessingException.class)
    public static <T> T checkAndGetCommand(Class<T> clazz, String json) {
        final T command = MAPPER.readValue(json, clazz);
        return checkAndGetCommand(command);
    }

    public static <T> T checkAndGetCommand(T command) {
        final var validatorFactory = Validation.buildDefaultValidatorFactory();
        final var validator = validatorFactory.getValidator();
        final var violations = validator.validate(command);
        if (J.nonEmpty(violations)) {
            throw new ConstraintViolationException(violations);
        }
        return command;
    }

    public static Optional<Span> extractSpan(final Optional<Tracer> tracerOpt, final String operationName, final Message message) {
        return tracerOpt.map(tracer -> {
            final var spanBuilder = tracer.buildSpan(operationName);
            final var map = J.<String, String>newHashMap();
            message.headers().forEach(entry -> map.put(entry.getKey(), entry.getValue()));
            ofNullable(tracer.extract(TEXT_MAP, new TextMapAdapter(map))).ifPresent(spanBuilder::asChildOf);
            return spanBuilder.start();
        });
    }

    public static Optional<Principal> extractPrincipal(final Message message) {
        return ofNullable(message.headers())
                .map(it -> it.get(Principal.class.getName()))
                .filter(J::nonBlank)
                .map(UserPrincipal::new);
    }

}
