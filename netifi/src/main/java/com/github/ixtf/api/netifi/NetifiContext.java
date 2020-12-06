package com.github.ixtf.api.netifi;

import com.github.ixtf.api.ApiContext;
import com.google.protobuf.Message;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.SignalType;

import java.util.Optional;

import static com.github.ixtf.api.netifi.NetifiUtil.addPrincipalTag;

public interface NetifiContext extends ApiContext {
    Logger log = LoggerFactory.getLogger(NetifiContext.class);

    <T extends Message> T message();

    String metadata(String key);

    default void finish(SignalType signalType) {
        addPrincipalTag(spanOpt(), principalOpt()).ifPresent(Span::finish);
    }

    default Optional<Span> fail(Throwable e) {
        log.error("", e);
        return spanOpt().map(span -> span.setTag(Tags.ERROR, true).log(e.getMessage()));
    }

}
