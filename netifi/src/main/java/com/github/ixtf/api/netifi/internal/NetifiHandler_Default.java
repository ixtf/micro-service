package com.github.ixtf.api.netifi.internal;

import com.github.ixtf.api.Util;
import com.github.ixtf.api.netifi.NetifiHandler;
import com.github.ixtf.api.netifi.NetifiService;
import com.github.ixtf.api.netifi.NetifiUtil;
import com.github.ixtf.api.netifi.proto.ApiRequest;
import com.github.ixtf.api.netifi.proto.ApiResponse;
import com.github.ixtf.japp.core.J;
import com.github.ixtf.japp.core.exception.JAuthenticationError;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.netifi.broker.BrokerClient;
import io.netty.buffer.ByteBufAllocator;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;
import io.rsocket.rpc.frames.Metadata;
import io.rsocket.util.ByteBufPayload;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.github.ixtf.api.Util.spanOpt;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static java.util.Optional.ofNullable;

@Slf4j
public class NetifiHandler_Default implements NetifiHandler {
    private final Function<RoutingContext, Future<Optional<Principal>>> principalFun;
    @Inject(optional = true)
    private Tracer tracer;
    @Inject
    private BrokerClient brokerClient;

    public NetifiHandler_Default(Function<RoutingContext, Future<Optional<Principal>>> principalFun) {
        this.principalFun = principalFun;
    }

    @Override
    public void handle(RoutingContext rc) {
        new NetifiClientContextAgent(rc, principalFun, ofNullable(tracer)).invoke();
    }

    private class NetifiClientContextAgent {
        private final RoutingContext rc;
        private final HttpServerRequest request;
        private final HttpServerResponse response;
        private final String service;
        private final String action;
        private final Future<Optional<Principal>> principalOptFuture;
        private final Optional<Tracer> tracerOpt;
        private final Optional<Span> spanOpt;
        private final Map<String, String> metadata = J.newHashMap();

        private NetifiClientContextAgent(RoutingContext rc, Function<RoutingContext, Future<Optional<Principal>>> principalFun, Optional<Tracer> tracerOpt) {
            this.rc = rc;
            request = rc.request();
            response = rc.response();
            principalOptFuture = principalFun.apply(rc);
            this.tracerOpt = tracerOpt;
            service = rc.pathParam(NetifiService.SERVICE);
            action = rc.pathParam(NetifiService.ACTION);
            spanOpt = tracerOpt.flatMap(tracer -> {
                final var map = J.<String, String>newHashMap();
                rc.request().headers().forEach(entry -> map.put(entry.getKey(), entry.getValue()));
                return spanOpt(tracerOpt, String.join(":", service, action), map);
            });
        }

        private void invoke() {
            principalOptFuture.onFailure(e -> fail(new JAuthenticationError())).onSuccess(principalOpt -> {
                principalOpt.map(Principal::getName).ifPresent(it -> metadata.put(Principal.class.getName(), it));
                tracerOpt.ifPresent(tracer -> spanOpt.ifPresent(span -> {
                    final var map = J.<String, String>newHashMap();
                    tracer.inject(span.context(), TEXT_MAP, new TextMapAdapter(map));
                    metadata.putAll(map);
                }));
                final var metadata = NetifiUtil.encodeMetadata(this.metadata);
                final var metadataBuf = Metadata.encode(ByteBufAllocator.DEFAULT, service, action, metadata);
                metadata.release();

                final var builder = ApiRequest.newBuilder();
                request.headers().forEach(entry -> builder.putHeaders(entry.getKey().toLowerCase(), entry.getValue()));
                rc.queryParams().forEach(entry -> builder.putQueryParams(entry.getKey(), entry.getValue()));
                rc.pathParams().forEach(builder::putPathParams);
                builder.removePathParams(NetifiService.SERVICE);
                builder.removePathParams(NetifiService.ACTION);
                final var apiRequest = builder.setBody(ByteString.copyFrom(rc.getBody().getBytes())).build();
                final var data = NetifiHandler.serialize(apiRequest);

                final var rSocket = brokerClient.groupServiceSocket(service);
                rSocket.requestResponse(ByteBufPayload.create(data, metadataBuf))
                        .map(NetifiHandler::deserializer)
                        .subscribe(this::end, this::fail);
            });
        }

//        private void invoke() {
//            principalOptFuture.onFailure(e -> fail(new JAuthenticationError())).onSuccess(principalOpt -> {
//                injectPrincipal(principalOpt, metadata);
//                injectSpan(tracerOpt, spanOpt, metadata);
//                final var builder = ApiRequest.newBuilder();
//                request.headers().forEach(entry -> builder.putHeaders(entry.getKey().toLowerCase(), entry.getValue()));
//                rc.queryParams().forEach(entry -> builder.putQueryParams(entry.getKey(), entry.getValue()));
//                rc.pathParams().forEach(builder::putPathParams);
//                final var apiRequest = builder.setBody(ByteString.copyFrom(rc.getBody().getBytes())).build();
//                netifiService.invoke(service, action, apiRequest, metadata).cast(ApiResponse.class)
//                        .doFinally(signalType -> addPrincipalTag(spanOpt, principalOpt).ifPresent(span -> span.setTag(Tags.HTTP_METHOD, request.method().name()).setTag(Tags.HTTP_URL, request.uri()).setTag(SERVICE, service).setTag(ACTION, action).finish()))
//                        .subscribe(this::end, this::fail);
//            });
//        }

        private void end(ApiResponse apiResponse) {
            final int statusCode = apiResponse.getStatus();
            response.setStatusCode(statusCode);
            if (statusCode >= 300 && statusCode < 400) {
                apiResponse.getHeadersMap().forEach((k, v) -> response.putHeader(k, v));
                response.end();
                return;
            }
            response.putHeader(CONTENT_TYPE, APPLICATION_JSON);
            apiResponse.getHeadersMap().forEach((k, v) -> response.putHeader(k, v));
            final ByteString body = apiResponse.getBody();
            if (body.isEmpty()) {
                response.end();
            } else {
                response.end(Buffer.buffer(body.toByteArray()));
            }
            spanOpt.ifPresent(span -> {
                span.setTag(Tags.HTTP_STATUS, statusCode);
                if (statusCode < 200 || statusCode >= 400) {
                    span.setTag(Tags.ERROR, true);
                }
                span.finish();
            });
        }

        private void fail(Throwable e) {
            log.error("", e);
            final int statusCode = e instanceof JAuthenticationError ? 401 : 500;
            final JsonObject jsonObject = new JsonObject().put(NetifiService.ERROR_MSG_NAME, e.getMessage());
            response.setStatusCode(statusCode).end(jsonObject.encode());
            spanOpt.ifPresent(span -> span.setTag(Tags.HTTP_STATUS, statusCode).setTag(Tags.ERROR, true).log(e.getMessage()).finish());
        }
    }

}
