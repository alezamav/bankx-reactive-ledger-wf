package com.bankx.logging;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class LogContext {
    private static final String CORR_ID = "corrId";
    public <T> Mono<T> withMdc(Mono<T> mono) {
        return Mono.deferContextual(ctx -> {
            String corr = String.valueOf(ctx.getOrDefault(CORR_ID, "na"));
            ThreadContext.put(CORR_ID, corr);
            return mono.doFinally(sig -> ThreadContext.remove(CORR_ID));
        });
    }
}
