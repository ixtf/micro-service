package com.github.ixtf.api;

import com.github.ixtf.japp.core.J;
import com.sun.security.auth.UserPrincipal;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.TextMapAdapter;
import io.vertx.core.eventbus.DeliveryOptions;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import static com.github.ixtf.api.Util.checkAndGetCommand;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

public interface ApiContext {

    Map<String, String> headers();

    byte[] body();

    Optional<Tracer> tracerOpt();

    Optional<Span> spanOpt();

    default String bodyAsString() {
        return new String(body(), UTF_8);
    }

    default <T> T command(Class<T> clazz) {
        return checkAndGetCommand(clazz, body());
    }

    default String header(String key) {
        return headers().get(key);
    }

    default String header(AsciiString key) {
        return headers().get(key.toString());
    }

    default Optional<Principal> principalOpt() {
        return ofNullable(header(Principal.class.getName()))
                .filter(J::nonBlank)
                .map(UserPrincipal::new);
    }

    default Principal principal() {
        return principalOpt().get();
    }

    default Optional<Span> spanOpt(final String operationName) {
        return tracerOpt().map(tracer -> {
            final var spanBuilder = tracer.buildSpan(operationName);
            ofNullable(headers()).map(TextMapAdapter::new).map(it -> tracer.extract(TEXT_MAP, it)).ifPresent(spanBuilder::asChildOf);
            return spanBuilder.start();
        });
    }

    default DeliveryOptions injectDeliveryOptions(Optional<Span> spanOpt) {
        final var deliveryOptions = new DeliveryOptions();
        tracerOpt().ifPresent(tracer -> spanOpt.map(Span::context).ifPresent(spanContext -> {
            final var map = J.<String, String>newHashMap();
            tracer.inject(spanContext, TEXT_MAP, new TextMapAdapter(map));
            map.forEach((k, v) -> deliveryOptions.addHeader(k, v));
        }));
        principalOpt().map(Principal::getName).ifPresent(it -> deliveryOptions.addHeader(Principal.class.getName(), it));
        return deliveryOptions;
    }

    default DeliveryOptions injectDeliveryOptions() {
        return injectDeliveryOptions(spanOpt());
    }

    default Map injectMap(Optional<Span> spanOpt) {
        final Map map = J.newHashMap();
        tracerOpt().ifPresent(tracer -> spanOpt.map(Span::context).ifPresent(spanContext -> tracer.inject(spanContext, TEXT_MAP, new TextMapAdapter(map))));
        principalOpt().map(Principal::getName).ifPresent(it -> map.put(Principal.class.getName(), it));
        return map;
    }

    default Map injectMap() {
        return injectMap(spanOpt());
    }

}
