package com.github.ixtf.api.netifi.internal;

import com.github.ixtf.api.Util;
import com.github.ixtf.api.netifi.NetifiContext;
import com.github.ixtf.api.netifi.NetifiUtil;
import com.github.ixtf.japp.core.J;
import com.google.protobuf.Message;
import com.sun.security.auth.UserPrincipal;
import io.netty.buffer.ByteBuf;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import static com.github.ixtf.api.netifi.NetifiUtil.decodeMetadata;
import static java.util.Optional.ofNullable;

@Accessors(fluent = true)
public class NetifiContext_Default implements NetifiContext {
    @Getter
    protected final Message message;
    @Getter
    protected final Map<String, String> metadata;
    @Getter
    protected final Optional<Principal> principalOpt;
    @Getter
    protected final Optional<Tracer> tracerOpt;
    @Getter
    protected final Optional<Span> spanOpt;

    NetifiContext_Default(final NetifiServerMethodInterceptor interceptor, final Message message, final ByteBuf byteBuf) {
        this.message = message;
        metadata = decodeMetadata(byteBuf);
        principalOpt = ofNullable(metadata)
                .map(it -> it.get(Principal.class.getName()))
                .filter(J::nonBlank)
                .map(UserPrincipal::new);
        tracerOpt = ofNullable(interceptor.getTracer());
        spanOpt = Util.spanOpt(tracerOpt, interceptor.getServiceClass().getSimpleName(), metadata).map(span -> span.setTag(Tags.COMPONENT, interceptor.getProxy().getClass().getName()).setTag("method", interceptor.getMethod().getName()));
    }

    @Override
    public <T> T command(Class<T> clazz) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> headers() {
        return Map.of();
    }

    @Override
    public String header(String key) {
        return headers().get(key);
    }

    @Override
    public byte[] body() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String metadata(String key) {
        return metadata.get(key);
    }
}
