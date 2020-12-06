package com.github.ixtf.api.netifi;

import com.github.ixtf.api.netifi.internal.NetifiServiceImpl;
import com.github.ixtf.api.netifi.proto.ApiResponse;
import com.google.inject.ImplementedBy;
import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.rsocket.rpc.RSocketRpcService;
import org.apache.commons.lang3.tuple.Pair;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.github.ixtf.api.netifi.NetifiUtil.encodeMetadata;

@ImplementedBy(NetifiServiceImpl.class)
public interface NetifiService {
    ApiResponse DEFAULT_API_RESPONSE = ApiResponse.newBuilder().setStatus(200).build();
    String SERVICE = "service";
    String ACTION = "action";
    String ERROR_CODE_NAME = "errCode";
    String ERROR_MSG_NAME = "errMsg";

    static String javaMethodName(String s) {
//        char c[] = s.toCharArray();
//        c[0] += 32;
//        return new String(c);
        char c[] = s.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }

    Mono<Void> addService(Class<? extends RSocketRpcService> serverClass);

    <T> T client(Class<T> clientClass);

    Mono<Message> invoke(Pair<Class<?>, String> pair, Message message, ByteBuf metadata);

    default Mono<Message> invoke(String serviceClassName, String action, Message message, Map<String, String> metadata) {
        return Mono.fromCallable(() -> Class.forName(serviceClassName)).flatMap(it -> invoke(it, action, message, metadata));
    }

    default Mono<Message> invoke(Class<?> serviceClass, String action, Message message, Map<String, String> metadata) {
        return invoke(Pair.of(serviceClass, action), message, encodeMetadata(metadata));
    }
}
