package com.github.ixtf.api;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;

import static io.vertx.core.VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        var deploymentOptions = new DeploymentOptions().setInstances(DEFAULT_EVENT_LOOP_POOL_SIZE);
        vertx.deployVerticle(ApiVerticle.class, deploymentOptions);
    }
}
