package com.github.ixtf.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

@Slf4j
public class ApiLauncher extends Launcher {

    public static void main(String[] args) {
        new ApiLauncher().dispatch(args);
    }

    @Override
    public void beforeStartingVertx(VertxOptions options) {
        final var prometheusOptions = new VertxPrometheusOptions().setEnabled(true).setPublishQuantiles(true);
        final var metricsOptions = new MicrometerMetricsOptions().setEnabled(true).setPrometheusOptions(prometheusOptions);
        options.setMaxEventLoopExecuteTime(Duration.ofSeconds(30).toNanos()).setMetricsOptions(metricsOptions);
        ofNullable(System.getProperty("hazelcast.local.publicAddress")).ifPresent(it -> {
            options.getEventBusOptions().setHost(it);
            options.getEventBusOptions().setClusterPublicHost(it);
        });
    }

    @Override
    public void afterStartingVertx(Vertx vertx) {
        final var config = ofNullable(getenv("API_ROOT_PATH"))
                .filter(it -> !it.isBlank())
                .or(() -> Optional.of("/data/api/config.yml"))
                .map(File::new)
                .filter(File::exists)
                .filter(File::canRead)
                .map(this::readYaml)
                .map(JsonNode::toString)
                .map(JsonObject::new)
                .orElseGet(JsonObject::new);
        ApiModule.init(vertx, config);
    }

    @SneakyThrows(IOException.class)
    private JsonNode readYaml(File file) {
        final var mapper = new YAMLMapper();
        return mapper.readTree(file);
    }

    @Override
    public void handleDeployFailed(Vertx vertx, String mainVerticle, DeploymentOptions deploymentOptions, Throwable cause) {
        log.error("", cause);
        super.handleDeployFailed(vertx, mainVerticle, deploymentOptions, cause);
    }

}
