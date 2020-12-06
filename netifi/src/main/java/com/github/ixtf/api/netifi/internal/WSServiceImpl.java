package com.github.ixtf.api.netifi.internal;

import com.github.ixtf.api.netifi.proto.ApiRequest;
import com.github.ixtf.api.netifi.proto.WSService;
import com.github.ixtf.api.ws.SockJsEvent;
import com.google.inject.Singleton;
import com.google.protobuf.Empty;
import io.netty.buffer.ByteBuf;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Singleton
public class WSServiceImpl implements WSService {

    @Override
    public Mono<Empty> fireSockJsEvent(ApiRequest request, ByteBuf metadata) {
        final var jsonObject = new JsonObject(request.getBody().toString(UTF_8));
        new SockJsEvent(jsonObject.getString("address"), jsonObject.getString("type"), jsonObject.getJsonObject("payload")).send();
        return Mono.empty();
    }
}
