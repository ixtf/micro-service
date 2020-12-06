package com.github.ixtf.api.netifi.internal;

import com.github.ixtf.api.ApiAction;
import com.github.ixtf.api.netifi.NetifiContext;
import com.github.ixtf.api.netifi.NetifiService;
import com.github.ixtf.api.netifi.NetifiUtil;
import com.github.ixtf.api.netifi.proto.ApiResponse;
import com.github.ixtf.japp.GuiceModule;
import com.google.inject.Inject;
import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.opentracing.Tracer;
import lombok.Getter;
import lombok.SneakyThrows;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Objects;

import static com.github.ixtf.japp.GuiceModule.getInstance;


public class NetifiServerMethodInterceptor {
    @Getter
    protected final Object proxy;
    @Getter
    protected final Method method;
    @Getter
    protected final Class<?> serviceClass;
    protected final String serviceAction;
    protected final Method serviceMethod;
    protected final ParameterizedType serviceGenericReturnType;
    protected final Class<?> serviceRawType;
    protected final Class<?> serviceActualType;
    @Getter
    @Inject(optional = true)
    protected Tracer tracer;

    protected NetifiServerMethodInterceptor(Method method, Class<?> serviceClass, String serviceAction, Method serviceMethod) {
        this.method = method;
        proxy = getInstance(method.getDeclaringClass());
        this.serviceClass = serviceClass;
        this.serviceAction = serviceAction;
        this.serviceMethod = serviceMethod;
        serviceGenericReturnType = (ParameterizedType) serviceMethod.getGenericReturnType();
        serviceRawType = (Class<?>) serviceGenericReturnType.getRawType();
        serviceActualType = (Class<?>) serviceGenericReturnType.getActualTypeArguments()[0];
    }

    @SneakyThrows(ClassNotFoundException.class)
    public static NetifiServerMethodInterceptor from(Method method) {
        if (method.getParameterCount() != 1 ||
                !NetifiContext.class.isAssignableFrom(method.getParameterTypes()[0])) {
            throw new RuntimeException("[" + method + "], 方法参数必须为 NetifiContext");
        }

        var annotation = method.getAnnotation(ApiAction.class);
        var serviceClass = Class.forName(annotation.service());
        var serviceAction = annotation.action();
        var serviceMethod = Arrays.stream(serviceClass.getDeclaredMethods())
                .filter(it -> Objects.equals(it.getName(), NetifiService.javaMethodName(serviceAction)))
                .findFirst().orElseThrow(() -> new RuntimeException("[" + serviceClass + "." + serviceAction + "], 找不到该方法"));
        var serviceGenericReturnType = (ParameterizedType) serviceMethod.getGenericReturnType();
        var serviceActualType = (Class<?>) serviceGenericReturnType.getActualTypeArguments()[0];

        if (ApiResponse.class.isAssignableFrom(serviceActualType)) {
            return new NetifiServerMethodInterceptor_RC(method, serviceClass, serviceAction, serviceMethod);
        }
        if (Objects.equals(method.getGenericReturnType(), serviceGenericReturnType)) {
            return new NetifiServerMethodInterceptor(method, serviceClass, serviceAction, serviceMethod);
        }
        throw new RuntimeException("[" + method + "], 方法不支持代理");
    }

    public boolean matchImplByAction(Class<?> clazz, Object action) {
        return Objects.equals(serviceClass, clazz) && Objects.equals(serviceAction, action);
    }

    public ElementMatcher<? super MethodDescription> elementMatcher() {
        return ElementMatchers.named(serviceMethod.getName());
    }

    @RuntimeType
    public Object intercept(@RuntimeType Message message, @RuntimeType ByteBuf byteBuf) {
        final var ctx = createCtx(message, byteBuf);
        final Flux<Message> messageFlux = Mono.fromCallable(() -> this.method.invoke(proxy, new Object[]{ctx})).flatMapMany(it -> {
            if (it instanceof Mono) {
                return ((Mono<?>) it).map(this::toMessage);
            }
            if (it instanceof Flux) {
                return ((Flux<?>) it).map(this::toMessage);
            }
            return Mono.just(toMessage(it));
        }).onErrorMap(NetifiUtil::onErrorMap);
        if (Mono.class.isAssignableFrom(serviceRawType)) {
            return handleMono(ctx, messageFlux.next());
        }
        if (Flux.class.isAssignableFrom(serviceRawType)) {
            return handleFlux(ctx, messageFlux);
        }
        throw new UnsupportedOperationException("" + serviceRawType);
    }

    protected Object handleMono(NetifiContext ctx, Mono<Message> messageMono) {
        return messageMono.doOnError(ctx::fail).doFinally(ctx::finish);
    }

    protected Object handleFlux(NetifiContext ctx, Flux<Message> messageFlux) {
        return messageFlux.doOnError(ctx::fail).doFinally(ctx::finish);
    }

    protected NetifiContext createCtx(Message message, ByteBuf byteBuf) {
        return new NetifiContext_Default(this, message, byteBuf);
    }

    protected Message toMessage(Object o) {
        return (Message) o;
    }

}
