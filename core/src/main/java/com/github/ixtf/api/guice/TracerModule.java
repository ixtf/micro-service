package com.github.ixtf.api.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.jaegertracing.Configuration;
import io.opentracing.Tracer;
import io.vertx.core.json.JsonObject;

import static com.github.ixtf.api.guice.ApiModule.CONFIG;

public class TracerModule extends AbstractModule {
    private final String serviceName;

    public TracerModule(String serviceName) {
        this.serviceName = serviceName;
    }

    @Singleton
    @Provides
    private Tracer Tracer(@Named(CONFIG) JsonObject rootConfig) {
        final var config = rootConfig.getJsonObject("tracer");
        final var agentHost = config.getString("agentHost", "dev.medipath.com.cn");
        final var samplerConfig = Configuration.SamplerConfiguration.fromEnv().withType("const").withParam(1);
        final var senderConfiguration = new Configuration.SenderConfiguration().withAgentHost(agentHost);
        final var reporterConfig = Configuration.ReporterConfiguration.fromEnv().withSender(senderConfiguration).withLogSpans(true);
        return new Configuration(serviceName).withSampler(samplerConfig).withReporter(reporterConfig).getTracer();
    }

}
