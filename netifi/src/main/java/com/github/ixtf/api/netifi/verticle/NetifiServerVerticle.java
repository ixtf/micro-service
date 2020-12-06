package com.github.ixtf.api.netifi.verticle;

import com.github.ixtf.api.netifi.NetifiService;
import com.google.inject.Inject;
import io.rsocket.rpc.RSocketRpcService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static com.github.ixtf.japp.GuiceModule.injectMembers;

public abstract class NetifiServerVerticle extends AbstractVerticle {
    @Inject
    private NetifiService netifiServer;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        injectMembers(this);
        Mono.fromCallable(this::serverClass)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(netifiServer::addService)
                .doOnSuccess(startPromise::complete)
                .doOnError(startPromise::fail)
                .subscribe();
    }

    protected abstract Class<? extends RSocketRpcService> serverClass();
}
