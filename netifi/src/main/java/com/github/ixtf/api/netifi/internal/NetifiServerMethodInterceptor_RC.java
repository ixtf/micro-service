package com.github.ixtf.api.netifi.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.ixtf.api.netifi.NetifiContext;
import com.github.ixtf.api.netifi.NetifiService;
import com.github.ixtf.api.netifi.proto.ApiResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static com.github.ixtf.japp.core.Constant.MAPPER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class NetifiServerMethodInterceptor_RC extends NetifiServerMethodInterceptor {

    NetifiServerMethodInterceptor_RC(Method method, Class<?> serviceClass, String serviceAction, Method serviceMethod) {
        super(method, serviceClass, serviceAction, serviceMethod);
    }

    @Override
    protected Object handleMono(NetifiContext ctx, Mono<Message> messageMono) {
        return messageMono.switchIfEmpty(Mono.defer(this::switchIfEmpty)).doOnError(ctx::fail).onErrorResume(this::onErrorResume).doFinally(ctx::finish);
    }

    @Override
    protected NetifiContext createCtx(Message message, ByteBuf byteBuf) {
        return new NetifiContext_RC(this, message, byteBuf);
    }

    private Mono<Message> switchIfEmpty() {
        return Mono.just(NetifiService.DEFAULT_API_RESPONSE);
    }

    private Mono<Message> onErrorResume(Throwable e) {
        return Mono.fromCallable(() -> {
            final var jsonObject = new JsonObject().put(NetifiService.ERROR_MSG_NAME, e.getMessage());
            final var byteString = ByteString.copyFrom(jsonObject.encode(), UTF_8);
            return ApiResponse.newBuilder().setBody(byteString).setStatus(400).build();
        });
    }

    @SneakyThrows(JsonProcessingException.class)
    @Override
    protected Message toMessage(Object o) {
        if (o == null) {
            return NetifiService.DEFAULT_API_RESPONSE;
        }
        if (o instanceof ApiResponse) {
            return super.toMessage(o);
        }
        final ByteString byteString;
        if (o instanceof String) {
            final String s = (String) o;
            byteString = ByteString.copyFrom(s, UTF_8);
        } else if (o instanceof JsonObject) {
            final JsonObject jsonObject = (JsonObject) o;
            byteString = ByteString.copyFrom(jsonObject.encode(), UTF_8);
        } else if (o instanceof JsonArray) {
            final JsonArray jsonArray = (JsonArray) o;
            byteString = ByteString.copyFrom(jsonArray.encode(), UTF_8);
        } else if (o instanceof byte[]) {
            final byte[] bytes = (byte[]) o;
            byteString = ByteString.copyFrom(bytes);
        } else {
            byteString = ByteString.copyFrom(MAPPER.writeValueAsBytes(o));
        }
        return ApiResponse.newBuilder().setStatus(200)
                .putHeaders(CONTENT_TYPE, APPLICATION_JSON)
                .setBody(byteString).build();
    }

}
