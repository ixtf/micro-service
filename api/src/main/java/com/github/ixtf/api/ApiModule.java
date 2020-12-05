package com.github.ixtf.api;

import com.google.inject.*;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.jaegertracing.Configuration;
import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.Set;
import java.util.stream.StreamSupport;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.vertx.ext.auth.oauth2.OAuth2FlowType.AUTH_CODE;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

public class ApiModule extends AbstractModule {
    private static Injector INJECTOR;
    private final Vertx vertx;
    private final JsonObject config;

    private ApiModule(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    synchronized public static void init(Vertx vertx, JsonObject config) {
        if (INJECTOR == null) {
            INJECTOR = Guice.createInjector(new ApiModule(vertx, config));
        }
    }

    public static void injectMembers(Object o) {
        INJECTOR.injectMembers(o);
    }

    @Override
    protected void configure() {
        bind(Vertx.class).toInstance(vertx);
        bind(JsonObject.class).annotatedWith(Names.named("RootConfig")).toInstance(config);
    }

    @Singleton
    @Provides
    private OAuth2ClientOptions OAuth2ClientOptions(@Named("RootConfig") JsonObject rootConfig) {
        final var config = rootConfig.getJsonObject("keycloak", new JsonObject());
        final var site = config.getString("site", "https://sso.medipath.com.cn/auth/realms/medipath");
        final var clientID = config.getString("clientID", "api");
        return new OAuth2ClientOptions().setSite(site).setClientID(clientID).setFlow(AUTH_CODE);
    }

    @Provides
    private CorsHandler CorsHandler(@Named("RootConfig") JsonObject rootConfig) {
        final var config = rootConfig.getJsonObject("cors", new JsonObject());
        final var allowedOriginPattern = ofNullable(config.getJsonArray("webOrigins"))
                .map(JsonArray::spliterator)
                .map(spliterator -> StreamSupport.stream(spliterator, true)
                        .distinct()
                        .map(it -> "(" + it + ")")
                        .collect(joining("|"))
                )
                .filter(it -> !it.isBlank())
                .orElse(".*.");
        final var allowedHeaders = Set.of(
                ACCESS_CONTROL_REQUEST_METHOD.toString(),
                ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(),
                ACCESS_CONTROL_ALLOW_ORIGIN.toString(),
                ACCESS_CONTROL_ALLOW_HEADERS.toString(),
                AUTHORIZATION.toString(),
                ACCEPT.toString(),
                ORIGIN.toString(),
                CONTENT_TYPE.toString()
        );
        return CorsHandler.create(allowedOriginPattern).allowedHeaders(allowedHeaders);
    }

    @Singleton
    @Provides
    private Tracer Tracer(@Named("RootConfig") JsonObject rootConfig) {
        final var config = rootConfig.getJsonObject("tracer", new JsonObject());
        final var agentHost = config.getString("agentHost", "dev.medipath.com.cn");
        final var samplerConfig = Configuration.SamplerConfiguration.fromEnv().withType("const").withParam(1);
        final var senderConfiguration = new Configuration.SenderConfiguration().withAgentHost(agentHost);
        final var reporterConfig = Configuration.ReporterConfiguration.fromEnv().withSender(senderConfiguration).withLogSpans(true);
        return new Configuration("api").withSampler(samplerConfig).withReporter(reporterConfig).getTracer();
    }
}
