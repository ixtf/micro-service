package com.github.ixtf.api;

import com.google.inject.Inject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions;
import io.vertx.ext.auth.oauth2.impl.OAuth2TokenImpl;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.micrometer.PrometheusScrapingHandler;
import lombok.extern.slf4j.Slf4j;

import java.security.Principal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;

import static com.github.ixtf.api.ApiModule.injectMembers;
import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_OCTET_STREAM;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP;
import static io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth.discover;
import static java.util.Optional.ofNullable;

@Slf4j
public class ApiVerticle extends AbstractVerticle {
    @Inject(optional = true)
    private Tracer tracer;
    @Inject
    private OAuth2ClientOptions oAuth2ClientOptions;
    @Inject
    private CorsHandler corsHandler;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        injectMembers(this);
        Future.<OAuth2Auth>future(p -> discover(vertx, oAuth2ClientOptions, p))
                .map(OAuth2AuthHandler::create)
                .flatMap(this::createHttpServer)
                .<Void>mapEmpty()
                .onComplete(startPromise);
    }

    private Future<HttpServer> createHttpServer(final OAuth2AuthHandler oauth2Handler) {
        return Future.future(p -> {
            final var router = Router.router(vertx);
            router.route().handler(corsHandler);
            router.route().handler(BodyHandler.create());
            router.route("/metrics").handler(PrometheusScrapingHandler.create());
            router.route("/health*").handler(HealthCheckHandler.create(vertx));
            router.route("/ping*").handler(HealthCheckHandler.createWithHealthChecks(HealthChecks.create(vertx)));

            final var permitted = new PermittedOptions().setAddressRegex("medipath://ws/.+");
            final var sockJSBridgeOptions = new SockJSBridgeOptions().addOutboundPermitted(permitted);
            router.mountSubRouter("/eventbus", SockJSHandler.create(vertx).bridge(sockJSBridgeOptions));

            router.route("/api/services/:service/actions/:action").handler(oauth2Handler).handler(this::handleApi);
            router.route("/dl/services/:service/actions/:action/tokens/:token").handler(this::handleDl);

            final var httpServerOptions = new HttpServerOptions().setCompressionSupported(true);
            vertx.createHttpServer(httpServerOptions).requestHandler(router).listen(9998, p);
        });
    }

    private void handleApi(RoutingContext rc) {
        final var address = apiAddress(rc);
        final var spanOpt = spanOpt(rc, address);
        final var deliveryOptions = deliveryOptions(rc, spanOpt);
        final var user = (OAuth2TokenImpl) rc.user();
        final var accessToken = user.accessToken();
        final var principal = accessToken.getString("sub");
        deliveryOptions.addHeader(Principal.class.getName(), principal);
        vertx.eventBus().request(address, rc.getBody(), deliveryOptions, ar -> {
            rc.response().putHeader(CONTENT_TYPE, APPLICATION_JSON);
            onComplete(rc, ar, spanOpt);
        });
    }

    private void handleDl(RoutingContext rc) {
        final var address = apiAddress(rc);
        final var spanOpt = spanOpt(rc, address);
        final var deliveryOptions = deliveryOptions(rc, spanOpt);
        if (deliveryOptions.getSendTimeout() <= DeliveryOptions.DEFAULT_TIMEOUT) {
            deliveryOptions.setSendTimeout(Duration.ofMinutes(5).toMillis());
        }
        final var token = rc.pathParam("token");
        vertx.eventBus().request(address, token, deliveryOptions, ar -> {
            rc.response().putHeader(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
            onComplete(rc, ar, spanOpt);
        });
    }

    private String apiAddress(RoutingContext rc) {
        final var service = rc.pathParam("service");
        final var action = rc.pathParam("action");
        return String.join(":", service, action);
    }

    private Optional<Span> spanOpt(RoutingContext rc, String address) {
        return ofNullable(tracer).map(tracer -> {
            final var map = new HashMap<String, String>();
            rc.request().headers().forEach(entry -> map.put(entry.getKey(), entry.getValue()));
            final var spanBuilder = tracer.buildSpan(address);
            final var spanContext = tracer.extract(TEXT_MAP, new TextMapAdapter(map));
            if (spanContext != null) {
                spanBuilder.asChildOf(spanContext);
            }
            return spanBuilder.start();
        });
    }

    private DeliveryOptions deliveryOptions(RoutingContext rc, Optional<Span> spanOpt) {
        final var deliveryOptions = new DeliveryOptions();
        spanOpt.ifPresent(span -> {
            final var map = new HashMap<String, String>();
            tracer.inject(span.context(), TEXT_MAP, new TextMapAdapter(map));
            map.forEach((k, v) -> deliveryOptions.addHeader(k, v));
        });
        ofNullable(rc.queryParam("timeout"))
                .filter(it -> it.size() > 0)
                .map(it -> it.get(0))
                .map(Long::parseLong)
                .filter(it -> it > DeliveryOptions.DEFAULT_TIMEOUT)
                .ifPresent(deliveryOptions::setSendTimeout);
        rc.request().headers().forEach(it -> {
            final var key = it.getKey().toLowerCase();
            if (!AUTHORIZATION.toString(0).equals(key)) {
                deliveryOptions.addHeader(key, it.getValue());
            }
        });
        return deliveryOptions;
    }

    private void onComplete(RoutingContext rc, AsyncResult<Message<Object>> ar, Optional<Span> spanOpt) {
        try {
            if (ar.failed()) {
                onFailure(rc, ar.cause(), spanOpt);
            } else {
                onSuccess(rc, ar.result(), spanOpt);
            }
        } catch (Throwable e) {
            onFailure(rc, e, spanOpt);
        } finally {
            spanOpt.ifPresent(Span::finish);
        }
    }

    private void onSuccess(RoutingContext rc, Message<Object> message, Optional<Span> spanOpt) {
        final var response = rc.response();
        message.headers().forEach(it -> {
            if (it.getKey().equals(HttpResponseStatus.class.getName())) {
                final var status = HttpResponseStatus.parseLine(it.getValue());
                response.setStatusCode(status.code());
            } else {
                response.putHeader(it.getKey(), it.getValue());
            }
        });
        final var statusCode = response.getStatusCode();
        if (statusCode >= 300 && statusCode < 400) {
            response.end();
        } else {
            final var body = message.body();
            if (body == null) {
                response.end();
            } else if (body instanceof Buffer) {
                response.end((Buffer) body);
            } else if (body instanceof byte[]) {
                final var bytes = (byte[]) body;
                response.end(Buffer.buffer(bytes));
            } else if (body instanceof String) {
                response.end((String) body);
            } else {
                onFailure(rc, new RuntimeException("body must be (null | Buffer | byte[] | String)"), spanOpt);
            }
        }
    }

    private void onFailure(RoutingContext rc, Throwable e, Optional<Span> spanOpt) {
        rc.fail(e);
        log.error(apiAddress(rc), e);
        spanOpt.ifPresent(it -> it.setTag(Tags.ERROR, true).log(e.getMessage()));
    }

}
