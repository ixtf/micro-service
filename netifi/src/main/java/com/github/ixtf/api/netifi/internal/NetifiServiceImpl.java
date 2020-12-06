package com.github.ixtf.api.netifi.internal;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.ixtf.api.netifi.NetifiModule;
import com.github.ixtf.api.netifi.NetifiService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.Message;
import com.netifi.broker.BrokerClient;
import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import io.rsocket.rpc.RSocketRpcService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import org.apache.commons.lang3.tuple.Pair;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.github.ixtf.api.netifi.NetifiService.javaMethodName;
import static java.util.stream.Collectors.toList;

@Slf4j
@Singleton
public class NetifiServiceImpl implements NetifiService {
    private final LoadingCache<Pair<Class<?>, String>, BiFunction<Message, ByteBuf, Mono<Message>>> CLIENT_ACTION_CACHE = Caffeine.newBuilder().build(new CacheLoader<>() {
        @Nullable
        @Override
        public BiFunction<Message, ByteBuf, Mono<Message>> load(@NonNull Pair<Class<?>, String> pair) throws Exception {
            final var action = pair.getValue();
            final var serviceClass = pair.getKey();
            final var client = CLIENT_CACHE.get(serviceClass);
            final var methods = Arrays.stream(client.getClass().getDeclaredMethods()).parallel().filter(method -> {
                if (method.getParameterCount() != 2) {
                    return false;
                }
                final String methodName = method.getName();
                return Objects.equals(methodName, action);
            }).collect(toList());
            final var method = checkAndGetOnlyOne(methods, () -> "未实现接口方法：[" + serviceClass + "." + action + "]", () -> "重复实现接口方法：[" + serviceClass + "." + action + "]");
            return (message, metadata) -> Mono.fromCallable(() -> (Mono<Message>) method.invoke(client, message, metadata)).flatMap(Function.identity());
        }
    });
    @Inject
    private BrokerClient brokerClient;
    private final LoadingCache<Class<?>, Object> CLIENT_CACHE = Caffeine.newBuilder().build(new CacheLoader<>() {
        @Nullable
        @Override
        public Object load(@NonNull Class<?> serviceClass) throws Exception {
            final var clientClass = Class.forName(serviceClass.getName() + "Client");
            final var conn = brokerClient.groupServiceSocket(serviceClass.getName());
            return clientClass.getConstructor(RSocket.class).newInstance(conn);
        }
    });
    @Inject
    @Named(NetifiModule.NETIFI_SERVER_METHOD_INTERCEPTORS)
    private Collection<NetifiServerMethodInterceptor> INTERCEPTORS;
    private final LoadingCache<Class<? extends RSocketRpcService>, RSocketRpcService> SERVER_CACHE = Caffeine.newBuilder().build(new CacheLoader<>() {
        @Nullable
        @Override
        public RSocketRpcService load(@NonNull Class<? extends RSocketRpcService> serverClass) throws Exception {
            // "Server".length() == 6
            final var serverClassName = serverClass.getName();
            final var serviceClass = Class.forName(serverClassName.substring(0, serverClassName.length() - 6));
            var builder = new ByteBuddy().subclass(serviceClass);
            for (var field : serviceClass.getDeclaredFields()) {
                if (field.getName().startsWith("METHOD_")) {
                    final var serviceAction = field.get(serviceClass);
                    final var interceptors = INTERCEPTORS.parallelStream()
                            .filter(it -> it.matchImplByAction(serviceClass, serviceAction))
                            .collect(toList());
                    final var interceptor = checkAndGetOnlyOne(interceptors, () -> "未实现接口方法：[" + serviceClass + "." + serviceAction + "]", () -> "重复实现接口方法：[" + serviceClass + "." + serviceAction + "]");
                    builder = builder.method(interceptor.elementMatcher()).intercept(MethodDelegation.to(interceptor));
                }
            }
            final var service = builder.make()
                    .load(this.getClass().getClassLoader())
                    .getLoaded()
                    .getDeclaredConstructor()
                    .newInstance();
            return serverClass.getDeclaredConstructor(serviceClass, Optional.class, Optional.class)
                    .newInstance(service, Optional.empty(), Optional.empty());
        }
    });

    private <T> T checkAndGetOnlyOne(List<T> ts, Supplier<String> s0, Supplier<String> s1) {
        if (ts.size() < 1) {
            final String msg = s0.get();
            log.error(msg);
            throw new RuntimeException(msg);
        }
        if (ts.size() > 1) {
            final String msg = s1.get();
            log.error(msg);
            throw new RuntimeException(msg);
        }
        return ts.get(0);
    }

    @Override
    public Mono<Void> addService(Class<? extends RSocketRpcService> serverClass) {
        return Mono.fromCallable(() -> SERVER_CACHE.get(serverClass))
                .doOnError(e -> log.error("[" + serverClass + "] 添加服务失败", e))
                .doOnSuccess(brokerClient::addService)
                .then();
    }

    @SneakyThrows(ClassNotFoundException.class)
    @Override
    public <T> T client(Class<T> clazz) {
        if (clazz.isInterface()) {
            return (T) CLIENT_CACHE.get(clazz);
        }
        final String clientClassName = clazz.getName();
        // "Client".length() == 6
        final String serviceClassName = clientClassName.substring(0, clientClassName.length() - 6);
        return (T) CLIENT_CACHE.get(Class.forName(serviceClassName));
    }

    @Override
    public Mono<Message> invoke(Pair<Class<?>, String> pair, Message message, ByteBuf metadata) {
        return Mono.defer(() -> {
            final Pair<Class<?>, String> key = Pair.of(pair.getLeft(), javaMethodName(pair.getRight()));
            return CLIENT_ACTION_CACHE.get(key).apply(message, metadata);
        });
    }

}
