package com.github.ixtf.api.vertx;

import com.github.ixtf.api.ApiContext;
import com.google.common.collect.Maps;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;

import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

public class VertxContext implements ApiContext {
    private final ReplyHandler replyHandler;
    private final Message reply;
    private final Optional<Span> spanOpt;

    public VertxContext(ReplyHandler replyHandler, Message reply) {
        this.replyHandler = replyHandler;
        this.reply = reply;
        spanOpt = spanOpt(replyHandler.getOperationName());
    }

    @Override
    public Map<String, String> headers() {
        final var ret = Maps.<String, String>newHashMap();
        reply.headers().forEach(entry -> ret.put(entry.getKey(), entry.getValue()));
        return ret;
    }

    @Override
    public byte[] body() {
        final var body = reply.body();
        if (body instanceof Buffer) {
            final var buffer = (Buffer) body;
            return buffer.getBytes();
        }
        final var s = (String) body;
        return s.getBytes(UTF_8);
    }

    @Override
    public Optional<Tracer> tracerOpt() {
        return ofNullable(replyHandler.getTracer());
    }

    @Override
    public Optional<Span> spanOpt() {
        return spanOpt;
    }
}
