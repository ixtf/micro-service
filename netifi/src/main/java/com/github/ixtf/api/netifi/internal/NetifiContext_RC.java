package com.github.ixtf.api.netifi.internal;

import com.github.ixtf.api.netifi.proto.ApiRequest;
import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import lombok.experimental.Accessors;

import java.util.Map;

@Accessors(fluent = true)
public class NetifiContext_RC extends NetifiContext_Default {

    NetifiContext_RC(NetifiServerMethodInterceptor interceptor, Message message, ByteBuf byteBuf) {
        super(interceptor, message, byteBuf);
    }

    @Override
    public Map<String, String> headers() {
        final var apiRequest = (ApiRequest) this.message;
        return apiRequest.getHeadersMap();
    }

    @Override
    public byte[] body() {
        final var apiRequest = (ApiRequest) this.message;
        return apiRequest.getBody().toByteArray();
    }

}
