package com.github.ixtf.api;

import com.google.common.collect.Maps;
import io.netty.util.AsciiString;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.github.ixtf.japp.core.Constant.MAPPER;
import static java.nio.charset.StandardCharsets.UTF_8;

@Accessors(chain = true)
public class ApiResponse {
    @Getter
    @Setter
    private int status = 200;
    @Getter
    private Map<String, String> headers = Maps.newConcurrentMap();
    @Setter
    private Object body;

    public ApiResponse putHeaders(final String key, final String value) {
        headers.put(key, value);
        return this;
    }

    public ApiResponse putHeaders(final AsciiString key, final AsciiString value) {
        return putHeaders(key.toString(), value.toString());
    }

    public ApiResponse putHeaders(final AsciiString key, final String value) {
        return putHeaders(key.toString(), value);
    }

    public Mono<?> bodyMono() {
        return bodyMono(body);
    }

    public static Mono<?> bodyMono(Object o) {
        if (o == null) {
            return Mono.just(StringUtils.EMPTY);
        }
        if (o instanceof String) {
            return Mono.just((String) o);
        }
        if (o instanceof byte[] || o instanceof ApiResponse) {
            return Mono.just(o);
        }
        if (o instanceof JsonObject) {
            final var v = (JsonObject) o;
            return Mono.just(v.encode().getBytes(UTF_8));
        }
        if (o instanceof JsonArray) {
            final var v = (JsonArray) o;
            return Mono.just(v.encode().getBytes(UTF_8));
        }
        if (o instanceof Mono) {
            final var v = (Mono) o;
            return v.flatMap(ApiResponse::bodyMono).defaultIfEmpty(StringUtils.EMPTY);
        }
        if (o instanceof Flux) {
            final var v = (Flux) o;
            // todo 集合类里面包含 JsonObject｜JsonArray
            return v.collectList().flatMap(ApiResponse::bodyMono);
        }
        return Mono.fromCallable(() -> MAPPER.writeValueAsBytes(o));
    }
}
