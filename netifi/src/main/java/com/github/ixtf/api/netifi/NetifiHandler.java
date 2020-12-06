package com.github.ixtf.api.netifi;

import com.github.ixtf.api.netifi.internal.NetifiHandler_Default;
import com.github.ixtf.api.netifi.proto.ApiResponse;
import com.github.ixtf.japp.GuiceModule;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import io.rsocket.Payload;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.security.Principal;
import java.util.Optional;
import java.util.function.Function;

public interface NetifiHandler extends Handler<RoutingContext> {

    static NetifiHandler create() {
        return create(rc -> Future.succeededFuture(Optional.empty()));
    }

    static NetifiHandler create(Function<RoutingContext, Future<Optional<Principal>>> principalFun) {
        return GuiceModule.injectMembers(new NetifiHandler_Default(principalFun));
    }

    static ByteBuf serialize(final MessageLite message) {
        final var length = message.getSerializedSize();
        final var byteBuf = ByteBufAllocator.DEFAULT.buffer(length);
        try {
            message.writeTo(CodedOutputStream.newInstance(byteBuf.internalNioBuffer(0, length)));
            byteBuf.writerIndex(length);
            return byteBuf;
        } catch (Throwable t) {
            ReferenceCountUtil.safeRelease(byteBuf);
            throw new RuntimeException(t);
        }
    }

    static ApiResponse deserializer(final Payload payload) {
        try {
            final var is = CodedInputStream.newInstance(payload.getData());
            return ApiResponse.parseFrom(is);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            ReferenceCountUtil.safeRelease(payload);
        }
    }
}
