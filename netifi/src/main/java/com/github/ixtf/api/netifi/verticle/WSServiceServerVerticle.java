package com.github.ixtf.api.netifi.verticle;

import com.github.ixtf.api.netifi.internal.WSServiceImpl;
import com.github.ixtf.api.netifi.proto.WSServiceServer;
import com.google.inject.Inject;
import com.netifi.broker.BrokerClient;
import io.vertx.core.AbstractVerticle;

import java.util.Optional;

import static com.github.ixtf.japp.GuiceModule.injectMembers;

public class WSServiceServerVerticle extends AbstractVerticle {
    @Inject
    private BrokerClient brokerClient;
    @Inject
    private WSServiceImpl impl;

    @Override
    public void start() throws Exception {
        injectMembers(this);
        brokerClient.addService(new WSServiceServer(impl, Optional.empty(), Optional.empty()));
    }

}
