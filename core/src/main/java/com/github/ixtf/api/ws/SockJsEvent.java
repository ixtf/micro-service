package com.github.ixtf.api.ws;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.io.Serializable;

import static com.github.ixtf.japp.GuiceModule.getInstance;

public class SockJsEvent implements Serializable {
    private final String address;
    private final String type;
    private final Object payload;

    public SockJsEvent(String address, String type, Object payload) {
        this.address = address;
        this.type = type;
        this.payload = payload;
    }

    public void send() {
        final var vertx = getInstance(Vertx.class);
        final var data = new JsonObject().put("type", type).put("payload", JsonObject.mapFrom(payload));
        vertx.eventBus().send(address, data);
    }

}
