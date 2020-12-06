package com.github.ixtf.api.netifi;

import com.github.ixtf.japp.core.J;
import io.netty.buffer.ByteBuf;
import io.opentracing.Span;
import io.rsocket.metadata.CompositeMetadata;
import io.rsocket.rpc.frames.Metadata;

import java.lang.reflect.InvocationTargetException;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import static io.netty.buffer.ByteBufAllocator.DEFAULT;
import static io.rsocket.metadata.CompositeMetadataFlyweight.encodeAndAddMetadata;
import static java.nio.charset.StandardCharsets.UTF_8;

public class NetifiUtil {

    public static Throwable onErrorMap(Throwable e) {
        if (e instanceof InvocationTargetException) {
            return ((InvocationTargetException) e).getTargetException();
        }
        return e;
    }

    public static ByteBuf encodeMetadata(final Map<String, String> metadata) {
        final var byteBuf = DEFAULT.compositeBuffer();
        metadata.forEach((k, v) -> encodeAndAddMetadata(byteBuf, DEFAULT, k, DEFAULT.buffer().writeBytes(v.getBytes(UTF_8))));
        return byteBuf;
    }

    public static Map<String, String> decodeMetadata(final ByteBuf byteBuf) {
        final var metadata = Metadata.getMetadata(byteBuf);
        final var map = J.<String, String>newHashMap();
        final var compositeMetadata = new CompositeMetadata(metadata, true);
        compositeMetadata.forEach(entry -> {
            byte[] bytes = new byte[entry.getContent().readableBytes()];
            entry.getContent().readBytes(bytes);
            map.put(entry.getMimeType(), new String(bytes, UTF_8));
        });
        return map;
    }

    public static Optional<Span> addPrincipalTag(Optional<Span> spanOpt, Optional<Principal> principalOpt) {
        spanOpt.ifPresent(span -> principalOpt.map(Principal::getName).ifPresent(it -> span.setTag(Principal.class.getName(), it)));
        return spanOpt;
    }
}
